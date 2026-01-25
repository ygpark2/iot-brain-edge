ifneq (,$(wildcard .env))
  include .env
  export
endif

SHELL := /bin/bash

STACK ?= brain
STACK_NAME ?= $(STACK)
STACK_FILE ?= ./deploy/swarm/stack.yml

# Kafka
KAFKA_SERVICE ?= $(STACK)_kafka
KAFKA_BOOTSTRAP_SERVERS ?= localhost:9094
KAFKA_TOPICS_PRG ?= /opt/kafka/bin/kafka-topics.sh
TOPIC_RAW ?= rawframes
TOPIC_RAW_PROCESSED ?= rawframes-processed
TOPIC_FEATURES ?= session-features
TOPIC_FEATURES_BASE ?= features-base
TOPIC_FEATURES_HEALTH ?= features-health
TOPIC_FEATURES_POWER ?= features-power
TOPIC_FEATURES_ENV ?= features-env
TOPIC_FEATURES_DLQ ?= session-features-dlq
TOPIC_INFERENCE_REQUESTS ?= inference-requests
TOPIC_INFERENCE_RESULTS ?= inference-results
TOPIC_INFERENCE_RESULTS_DLQ ?= inference-results-dlq
TOPIC_INFERENCE_ALERTS ?= inference-alerts
TOPIC_INFERENCE_ALERTS_DLQ ?= inference-alerts-dlq
TOPIC_RAW_PROCESSED_DLQ ?= rawframes-processed-dlq
# 운영 기본값: 파티션은 늘릴 수 있지만 줄이기 어렵다.
KAFKA_PARTITIONS ?= 3
# 단일 노드면 1, 브로커 3개면 3
KAFKA_REPLICATION ?= 1

# sbt
SBT ?= sbt

# Flink
FLINK_JOBMANAGER_SERVICE ?= $(STACK)_flink-jobmanager
FLINK_CLI ?= /opt/flink/bin/flink
FLINK_JAR ?= pipelines/flink-jobs/target/scala-3.7.4/flink-jobs-assembly.jar
FLINK_BG_PID ?= /tmp/flink-run-all.pid

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
	@echo "  make topics-pipeline   - create pipeline topics"
	@echo "  make topic-list        - list topics"
	@echo ""
	@echo "App (sbt):"
	@echo "  make sbt-clean"
	@echo "  make run-ingest"
	@echo "  make run-alert"
	@echo "  make run-edge-http     - edge-agent -> HTTP ingest"
	@echo "  make run-edge-kafka    - edge-agent -> Kafka (after KafkaProducer added)"
	@echo "  make run-flink JOB=<name> [FLINK_CONFIG_YAML=path]"
	@echo "  make flink-jar         - build flink fat jar"
	@echo "  make run-flink-cluster JOB=<name> [FLINK_CONFIG_YAML=path]"
	@echo "  make run-all-flink     - submit all flink jobs to cluster (sequential)"
	@echo "  make run-all-flink-bg  - submit all flink jobs in background"
	@echo "  make stop-all-flink    - stop background submit + cancel all flink jobs"
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
	docker service logs -f $(KAFKA_SERVICE)

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
$(shell docker ps --filter "name=$(KAFKA_SERVICE)\.1" --format "{{.ID}}" | head -n 1)
endef

define FLINK_JM_CONTAINER_ID
$(shell docker ps --filter "name=$(FLINK_JOBMANAGER_SERVICE)\.1" --format "{{.ID}}" | head -n 1)
endef

.PHONY: topic-create
topic-create:
	@if [ -z "$(KAFKA_CONTAINER_ID)" ]; then echo "Kafka container not found. Is the stack up?"; exit 1; fi
	docker exec -it $(KAFKA_CONTAINER_ID) $(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --create --if-not-exists --topic $(TOPIC_RAW) --partitions 3 --replication-factor 1

.PHONY: topic-list
topic-list:
	@if [ -z "$(KAFKA_CONTAINER_ID)" ]; then echo "Kafka container not found. Is the stack up?"; exit 1; fi
	docker exec -it $(KAFKA_CONTAINER_ID) $(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --list

.PHONY: kafka-wait
kafka-wait:
	@cid="$(KAFKA_CONTAINER_ID)"; \
	if [ -z "$$cid" ]; then \
	  echo "Kafka container not found. Is the stack up? (STACK=$(STACK) KAFKA_SERVICE=$(KAFKA_SERVICE))"; \
	  exit 1; \
	fi; \
	echo "[kafka-wait] waiting for Kafka (container=$$cid) ..."; \
	for i in $$(seq 1 60); do \
	  docker exec -i $$cid bash -lc '$(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --list >/dev/null 2>&1 || $(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --list >/dev/null 2>&1' && break; \
	  sleep 1; \
	  if [ $$i -eq 60 ]; then echo "[kafka-wait] Kafka not ready after 60s"; exit 1; fi; \
	done; \
	echo "[kafka-wait] Kafka is ready."

.PHONY: kafka-init
kafka-init: kafka-wait
	@cid="$(KAFKA_CONTAINER_ID)"; \
	echo "[kafka-init] creating topics (partitions=$(KAFKA_PARTITIONS), replication=$(KAFKA_REPLICATION)) ..."; \
	for t in "$(KAFKA_TOPIC)" "$(KAFKA_DLQ_TOPIC)"; do \
	  echo "[kafka-init] -> $$t"; \
	  docker exec -i $$cid bash -lc '\
	    $(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) \
	      --create --if-not-exists --topic '"$$t"' \
	      --partitions $(KAFKA_PARTITIONS) --replication-factor $(KAFKA_REPLICATION) \
	    || $(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) \
	      --create --if-not-exists --topic '"$$t"' \
	      --partitions $(KAFKA_PARTITIONS) --replication-factor $(KAFKA_REPLICATION) \
	  '; \
	done; \
	echo "[kafka-init] done. Listing topics:"; \
	docker exec -i $$cid bash -lc '\
	  $(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --list \
	  || $(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --list \
	'

.PHONY: create-kafka-topics
create-kafka-topics: kafka-wait
	@cid="$(KAFKA_CONTAINER_ID)"; \
	echo "[topics-pipeline] creating pipeline topics ..."; \
	for t in "$(TOPIC_RAW_PROCESSED)" "$(TOPIC_RAW_PROCESSED_DLQ)" "$(TOPIC_FEATURES)" "$(TOPIC_FEATURES_BASE)" "$(TOPIC_FEATURES_HEALTH)" "$(TOPIC_FEATURES_POWER)" "$(TOPIC_FEATURES_ENV)" "$(TOPIC_FEATURES_DLQ)" "$(TOPIC_INFERENCE_REQUESTS)" "$(TOPIC_INFERENCE_RESULTS)" "$(TOPIC_INFERENCE_RESULTS_DLQ)" "$(TOPIC_INFERENCE_ALERTS)" "$(TOPIC_INFERENCE_ALERTS_DLQ)"; do \
	  echo "[topics-pipeline] -> $$t"; \
	  docker exec -i $$cid bash -lc '\
	    $(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) \
	      --create --if-not-exists --topic '"$$t"' \
	      --partitions $(KAFKA_PARTITIONS) --replication-factor $(KAFKA_REPLICATION) \
	    || $(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) \
	      --create --if-not-exists --topic '"$$t"' \
	      --partitions $(KAFKA_PARTITIONS) --replication-factor $(KAFKA_REPLICATION) \
	  '; \
	done; \
	echo "[topics-pipeline] done. Listing topics:"; \
	docker exec -i $$cid bash -lc '\
	  $(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --list \
	  || $(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --list \
	'

.PHONY: kafka-describe
kafka-describe: kafka-wait
	@cid="$(KAFKA_CONTAINER_ID)"; \
	for t in "$(KAFKA_TOPIC)" "$(KAFKA_DLQ_TOPIC)"; do \
	  echo "----- $$t -----"; \
	  docker exec -i $$cid bash -lc '\
	    $(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --describe --topic '"$$t"' \
	    || $(KAFKA_TOPICS_PRG) --bootstrap-server $(KAFKA_BOOTSTRAP_SERVERS) --describe --topic '"$$t"' \
	  '; \
	done

.PHONY: ch-tail
ch-tail:
	@curl -u $(CLICKHOUSE_USER):$(CLICKHOUSE_PASSWORD) \
	  "http://localhost:8123/?query=SELECT%20ts,source,event_id,left(payload,200)%20FROM%20brain.rawframes%20ORDER%20BY%20ts%20DESC%20LIMIT%2010"

.PHONY: kafka-dlq-tail
kafka-dlq-tail:
	@echo "Consume DLQ (rawframes.dlq) ..."
	@docker exec -it $(KAFKA_CONTAINER_ID) \
	  bash -lc 'kafka-console-consumer --bootstrap-server localhost:9092 --topic rawframes.dlq --from-beginning'

.PHONY: kafka-dlq-consume
kafka-dlq-consume:
	@echo "Consuming DLQ messages (rawframes.dlq) ..."
	@docker exec -it $(KAFKA_CONTAINER_ID) \
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

.PHONY: run-alert
run-alert:
	$(SBT) "project alertService" run

.PHONY: run-flink
run-flink:
	@if [ -z "$(JOB)" ]; then echo "Usage: make run-flink JOB=<name> [FLINK_CONFIG_YAML=path]"; exit 1; fi
	@echo "Running Flink job: $(JOB)"
	FLINK_CONFIG_YAML=$(FLINK_CONFIG_YAML) $(SBT) "project flinkJobs" "runMain com.ainsoft.brain.flink.jobs.JobRunner $(JOB)"

.PHONY: flink-jar
flink-jar:
	@echo "Building Flink fat jar..."
	$(SBT) "project flinkJobs" assembly

.PHONY: run-flink-cluster
run-flink-cluster: flink-jar
	@if [ -z "$(JOB)" ]; then echo "Usage: make run-flink-cluster JOB=<name> [FLINK_CONFIG_YAML=path]"; exit 1; fi
	@if [ -z "$(FLINK_JM_CONTAINER_ID)" ]; then echo "Flink jobmanager container not found. Is the stack up?"; exit 1; fi
	@echo "Submitting Flink job to cluster: $(JOB)"
	@docker cp $(FLINK_JAR) $(FLINK_JM_CONTAINER_ID):/tmp/flink-jobs-assembly.jar
	@docker exec -e FLINK_CONFIG_YAML=$(FLINK_CONFIG_YAML) $(FLINK_JM_CONTAINER_ID) \
	  $(FLINK_CLI) run -c com.ainsoft.brain.flink.jobs.JobRunner /tmp/flink-jobs-assembly.jar $(JOB)

.PHONY: run-all-flink
run-all-flink:
	@echo "Submitting all Flink jobs sequentially..."
	@jobs=(sessionizer feature-base feature-health feature-power feature-env env-aggregator power-aggregator inference-trigger clickhouse-ingest); \
	for job in "$${jobs[@]}"; do \
	  $(MAKE) run-flink-cluster JOB="$$job"; \
	done; \
	:

.PHONY: run-all-flink-bg
run-all-flink-bg:
	@echo "Submitting all Flink jobs in background..."
	@nohup $(MAKE) run-all-flink >/tmp/flink-run-all.log 2>&1 & echo $$! > $(FLINK_BG_PID)
	@echo "PID: $$(cat $(FLINK_BG_PID)) (log: /tmp/flink-run-all.log)"

.PHONY: stop-all-flink
stop-all-flink:
	@if [ -f "$(FLINK_BG_PID)" ]; then \
	  pid="$$(cat $(FLINK_BG_PID))"; \
	  if kill -0 $$pid 2>/dev/null; then \
	    echo "Stopping background submit (pid=$$pid)"; \
	    kill $$pid; \
	  fi; \
	  rm -f $(FLINK_BG_PID); \
	fi
	@if [ -z "$(FLINK_JM_CONTAINER_ID)" ]; then echo "Flink jobmanager container not found. Is the stack up?"; exit 1; fi
	@echo "Cancelling all Flink jobs in cluster..."
	@docker exec $(FLINK_JM_CONTAINER_ID) $(FLINK_CLI) cancel -a

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
