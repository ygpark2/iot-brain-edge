ifneq (,$(wildcard .env))
  include .env
  export
endif

SHELL := /bin/bash

STACK ?= brain
STACK_NAME ?= $(STACK)
STACK_FILE ?= ./deploy/swarm/stack.yml

# Kafka
KAFKA_SVC ?= $(STACK)_kafka
KAFKA_BOOTSTRAP_SERVERS ?= localhost:9094
TOPIC_RAW ?= rawframes
# 운영 기본값: 파티션은 늘릴 수 있지만 줄이기 어렵다.
KAFKA_PARTITIONS ?= 3
# 단일 노드면 1, 브로커 3개면 3
KAFKA_REPLICATION ?= 1

# sbt
SBT ?= sbt

.PHONY: help
help:
	@echo "Swarm:"
	@echo "  make swarm-init        - docker swarm init (safe if already)"
	@echo "  make net               - create overlay network if needed"
	@echo "  make stack-up          - deploy stack"
	@echo "  make stack-down        - remove stack"
	@echo "  make stack-ps          - stack services"
	@echo "  make logs-kafka        - kafka logs"
	@echo "  make logs-kafka-ui     - kafka-ui logs"
	@echo "  make logs-clickhouse   - clickhouse logs"
	@echo "  make logs-flink        - flink logs (job/task)"
	@echo "  make clickhouse-ui     - show clickhouse url"
	@echo "  make flink-ui          - show flink ui url"
	@echo ""
	@echo "Kafka topics:"
	@echo "  make topic-create      - create $(TOPIC_RAW)"
	@echo "  make topic-list        - list topics"
	@echo ""
	@echo "App (sbt):"
	@echo "  make sbt-clean"
	@echo "  make run-ingest"
	@echo "  make run-edge-http     - edge-agent -> HTTP ingest"
	@echo "  make run-edge-kafka    - edge-agent -> Kafka (after KafkaProducer added)"
	@echo "  make run-sim           - sensor-sim"
	@echo ""
	@echo "One-shot:"
	@echo "  make dev-http          - swarm up + topic + run ingest + run edge(http)"
	@echo "  make dev-kafka         - swarm up + topic + run ingest + run edge(kafka)"

# -------- Swarm --------

.PHONY: swarm-init
swarm-init:
	@docker info --format '{{.Swarm.LocalNodeState}}' | grep -q active || docker swarm init

.PHONY: net-up
net-up:
	@docker network ls --format '{{.Name}}' | grep -q '^brain-net$$' || docker network create --driver overlay --attachable brain-net

.PHONY: ch-init
ch-init:
	@set -a; [ -f .env ] && . ./.env; set +a; \
	./deploy/clickhouse/init.sh

.PHONY: stack-up
stack-up: swarm-init net-up
	docker stack deploy -c $(STACK_FILE) $(STACK)
	@echo "Stack deployed: $(STACK)"
	@$(MAKE) ch-init

.PHONY: stack-down
stack-down:
	docker stack rm $(STACK)

.PHONY: stack-ps
stack-ps:
	docker stack services $(STACK)
	docker stack ps $(STACK)

# ---------------------- logs ------------------------
.PHONY: logs-kafka
logs-kafka:
	docker service logs -f $(KAFKA_SVC)

# NOTE: service names are prefixed by $(STACK)
.PHONY: logs-kafka-ui
logs-kafka-ui:
	docker service logs -f $(STACK)_kafka-ui

.PHONY: logs-clickhouse
logs-clickhouse:
	docker service logs -f $(STACK)_clickhouse

.PHONY: logs-flink
logs-flink:
	docker service logs -f $(STACK)_flink-jobmanager
	docker service logs -f $(STACK)_flink-taskmanager

# ------------------------ ui ------------------------
.PHONY: kafka-ui
kafka-ui:
	@echo "Kafka UI: http://localhost:8089"

.PHONY: flink-ui
flink-ui:
	@echo "Flink UI: http://localhost:8081"

.PHONY: clickhouse-ui
clickhouse-ui:
	@echo "ClickHouse HTTP: http://localhost:8123"

# -------- Kafka topic ops --------
# We exec kafka-topics.sh inside the kafka container by finding the running task container.
# (Works on single-node swarm; on multi-node, run on the manager hosting the task.)

define KAFKA_CONTAINER_ID
$(shell docker ps --filter "name=$(KAFKA_SVC)\.1" --format "{{.ID}}" | head -n 1)
endef

.PHONY: topic-create
topic-create:
	@if [ -z "$(KAFKA_CONTAINER_ID)" ]; then echo "Kafka container not found. Is the stack up?"; exit 1; fi
	docker exec -it $(KAFKA_CONTAINER_ID) /opt/kafka/bin/kafka-topics.sh --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --create --if-not-exists --topic $(TOPIC_RAW) --partitions 3 --replication-factor 1

.PHONY: topic-list
topic-list:
	@if [ -z "$(KAFKA_CONTAINER_ID)" ]; then echo "Kafka container not found. Is the stack up?"; exit 1; fi
	docker exec -it $(KAFKA_CONTAINER_ID) /opt/kafka/bin/kafka-topics.sh --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --list

# swarm에서 kafka 컨테이너 하나 잡기 (service label로 찾음)
define KAFKA_CID
$(shell docker ps -q --filter "label=com.docker.swarm.service.name=$(STACK)_$(KAFKA_SERVICE)" | head -n 1)
endef

.PHONY: kafka-wait
kafka-wait:
	@cid="$(KAFKA_CID)"; \
	if [ -z "$$cid" ]; then \
	  echo "Kafka container not found. Is the stack up? (STACK=$(STACK) KAFKA_SERVICE=$(KAFKA_SERVICE))"; \
	  exit 1; \
	fi; \
	echo "[kafka-wait] waiting for Kafka (container=$$cid) ..."; \
	for i in $$(seq 1 60); do \
	  docker exec -i $$cid bash -lc 'kafka-topics --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --list >/dev/null 2>&1 || /opt/kafka/bin/kafka-topics.sh --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --list >/dev/null 2>&1' && break; \
	  sleep 1; \
	  if [ $$i -eq 60 ]; then echo "[kafka-wait] Kafka not ready after 60s"; exit 1; fi; \
	done; \
	echo "[kafka-wait] Kafka is ready."

.PHONY: kafka-init
kafka-init: kafka-wait
	@cid="$(KAFKA_CID)"; \
	echo "[kafka-init] creating topics (partitions=$(KAFKA_PARTITIONS), replication=$(KAFKA_REPLICATION)) ..."; \
	for t in "$(KAFKA_TOPIC)" "$(KAFKA_DLQ_TOPIC)"; do \
	  echo "[kafka-init] -> $$t"; \
	  docker exec -i $$cid bash -lc '\
	    kafka-topics --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) \
	      --create --if-not-exists --topic '"$$t"' \
	      --partitions $(KAFKA_PARTITIONS) --replication-factor $(KAFKA_REPLICATION) \
	    || /opt/kafka/bin/kafka-topics.sh --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) \
	      --create --if-not-exists --topic '"$$t"' \
	      --partitions $(KAFKA_PARTITIONS) --replication-factor $(KAFKA_REPLICATION) \
	  '; \
	done; \
	echo "[kafka-init] done. Listing topics:"; \
	docker exec -i $$cid bash -lc '\
	  kafka-topics --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --list \
	  || /opt/kafka/bin/kafka-topics.sh --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --list \
	'

.PHONY: kafka-describe
kafka-describe: kafka-wait
	@cid="$(KAFKA_CID)"; \
	for t in "$(KAFKA_TOPIC)" "$(KAFKA_DLQ_TOPIC)"; do \
	  echo "----- $$t -----"; \
	  docker exec -i $$cid bash -lc '\
	    kafka-topics --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --describe --topic '"$$t"' \
	    || /opt/kafka/bin/kafka-topics.sh --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --describe --topic '"$$t"' \
	  '; \
	done

.PHONY: ch-tail
ch-tail:
	@curl -u $(CLICKHOUSE_USER):$(CLICKHOUSE_PASSWORD) \
	  "http://localhost:8123/?query=SELECT%20ts,source,event_id,left(payload,200)%20FROM%20brain.rawframes%20ORDER%20BY%20ts%20DESC%20LIMIT%2010"

.PHONY: kafka-dlq-tail
kafka-dlq-tail:
	@echo "Consume DLQ (rawframes.dlq) ..."
	@docker exec -it $$(docker ps -q --filter name=kafka | head -n 1) \
	  bash -lc 'kafka-console-consumer --bootstrap-server localhost:9092 --topic rawframes.dlq --from-beginning'

.PHONY: kafka-dlq-consume
kafka-dlq-consume:
	@echo "Consuming DLQ messages (rawframes.dlq) ..."
	@docker exec -it $$(docker ps -q --filter name=kafka | head -n 1) \
	  bash -lc 'kafka-console-consumer --bootstrap-server localhost:9092 --topic rawframes.dlq --from-beginning'


# -------- sbt --------

.PHONY: sbt-clean
sbt-clean:
	$(SBT) clean

.PHONY: run-ingest
run-ingest:
	$(SBT) run-ingest

.PHONY: run-edge-http
run-edge-http:
	@echo "PRODUCER_KIND=http INGEST_URL=http://127.0.0.1:8080/v1/ingest"
	$(SBT) "project edgeAgent" run

.PHONY: run-edge-kafka
run-edge-kafka:
	@echo "PRODUCER_KIND=kafka KAFKA_BOOTSTRAP_SERVERS=$(KAFKA_BOOTSTRAP_SERVERS) KAFKA_TOPIC=$(KAFKA_TOPIC)"
	$(SBT) "project edgeAgent" run

.PHONY: run-sim
run-sim:
	$(SBT) "run-sim -- --hz 5"

# -------- one-shot helpers --------

.PHONY: dev-http
dev-http: stack-up topic-create
	@echo "Kafka UI: http://localhost:8089"
	@echo "Now run in separate terminals:"
	@echo "  make run-ingest"
	@echo "  make run-edge-http"

.PHONY: dev-kafka
dev-kafka: stack-up topic-create
	@echo "Kafka UI: http://localhost:8089"
	@echo "Now run in separate terminals:"
	@echo "  make run-ingest"
	@echo "  make run-edge-kafka"
