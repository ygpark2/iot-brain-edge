<script>
	import { Cpu, Database, Share2, Zap, Activity, MessageSquare, Bell } from 'lucide-svelte';

	const nodes = [
		{ id: 'edge', name: 'Edge Agent', type: 'service', status: 'online', icon: Cpu, x: 20, y: 120 },
		{ id: 'ingest', name: 'Ingestion Svc', type: 'service', status: 'online', icon: Zap, x: 20, y: 260 },
		{ id: 't-raw', name: 'rawframes', type: 'topic', status: 'online', icon: MessageSquare, x: 240, y: 190 },
		{ id: 'flink', name: 'Flink Cluster', type: 'service', status: 'online', icon: Zap, x: 450, y: 190 },
		{ id: 't-req', name: 'inference-requests', type: 'topic', status: 'online', icon: MessageSquare, x: 680, y: 60 },
		{ id: 't-proc', name: 'rawframes-processed', type: 'topic', status: 'online', icon: MessageSquare, x: 680, y: 190 },
		{ id: 't-sess', name: 'session-features', type: 'topic', status: 'online', icon: MessageSquare, x: 680, y: 320 },
		{ id: 'inference', name: 'Inference Svc', type: 'service', status: 'online', icon: Activity, x: 920, y: 60 },
		{ id: 'alert', name: 'Alert Svc', type: 'service', status: 'online', icon: Bell, x: 920, y: 190 },
		{ id: 'clickhouse', name: 'ClickHouse', type: 'service', status: 'online', icon: Database, x: 920, y: 380 },
		{ id: 't-res', name: 'inference-results', type: 'topic', status: 'online', icon: MessageSquare, x: 1150, y: 60 },
		{ id: 't-alt', name: 'inference-alerts', type: 'topic', status: 'online', icon: MessageSquare, x: 1150, y: 190 }
	];

	const nodeW = 135;
	const nodeH = 65;
	const topicW = 125;
	const topicH = 45;

	const connections = [
		{ id: 'c1', from: 'edge', to: 't-raw' },
		{ id: 'c2', from: 'ingest', to: 't-raw' },
		{ id: 'c3', from: 't-raw', to: 'flink' },
		{ id: 'c4', from: 'flink', to: 't-proc' },
		{ id: 'c5', from: 'flink', to: 't-sess' },
		{ id: 'c6', from: 'flink', to: 't-req' },
		{ id: 'c7', from: 't-req', to: 'inference' },
		{ id: 'c8', from: 'inference', to: 't-res' },
		{ id: 'c9', from: 'inference', to: 't-alt' },
		{ id: 'c10', from: 't-alt', to: 'alert' }, // 수정 대상
		{ id: 'c11', from: 't-proc', to: 'clickhouse' },
		{ id: 'c12', from: 't-sess', to: 'clickhouse' },
		{ id: 'c13', from: 't-res', to: 'clickhouse' } 
	];

	function getPos(nodeId) {
		const node = nodes.find(n => n.id === nodeId);
		const w = node.type === 'service' ? nodeW : topicW;
		const h = node.type === 'service' ? nodeH : topicH;
		return {
			x: node.x, y: node.y, w, h,
			rightX: node.x + w, rightY: node.y + h / 2,
			leftX: node.x, leftY: node.y + h / 2
		};
	}

	function getPath(conn) {
		const start = getPos(conn.from);
		const end = getPos(conn.to);
		
		// 1. t-res -> clickhouse (기존 우회 경로)
		if (conn.from === 't-res' && conn.to === 'clickhouse') {
			const x1 = start.rightX; const y1 = start.rightY;
			const x2 = end.leftX; const y2 = end.leftY;
			const detourX = x1 + 50;
			const bottomY = 480; 
			const entryX = x2 - 40;
			return `M ${x1} ${y1} C ${detourX} ${y1}, ${detourX} ${y1}, ${detourX} ${y1 + 50} L ${detourX} ${bottomY - 20} C ${detourX} ${bottomY}, ${detourX} ${bottomY}, ${detourX - 20} ${bottomY} L ${entryX + 20} ${bottomY} C ${entryX} ${bottomY}, ${entryX} ${bottomY}, ${entryX} ${bottomY - 20} L ${entryX} ${y2} C ${entryX} ${y2}, ${entryX} ${y2}, ${x2} ${y2}`;
		}

		// 2. t-alt -> alert (새로운 우회 경로)
		if (conn.from === 't-alt' && conn.to === 'alert') {
			const x1 = start.rightX; const y1 = start.rightY;
			const x2 = end.leftX; const y2 = end.leftY;
			const detourX = x1 + 30; // t-alt 오른쪽 살짝 우회
			const detourY = 290;    // alert 노드 아래 공간
			const entryX = x2 - 30;  // alert 왼쪽 진입 대기
			
			return `M ${x1} ${y1} 
					C ${detourX} ${y1}, ${detourX} ${y1}, ${detourX} ${y1 + 30}
					L ${detourX} ${detourY - 20}
					C ${detourX} ${detourY}, ${detourX} ${detourY}, ${detourX - 20} ${detourY}
					L ${entryX + 20} ${detourY}
					C ${entryX} ${detourY}, ${entryX} ${detourY}, ${entryX} ${detourY - 20}
					L ${entryX} ${y2}
					C ${entryX} ${y2}, ${entryX} ${y2}, ${x2} ${y2}`;
		}

		// 일반적인 전/후방향 흐름
		const cpDist = Math.abs(end.leftX - start.rightX) * 0.45;
		if (start.rightX > end.leftX) {
			const cp1x = start.rightX + 50;
			const cp2x = end.leftX - 50;
			return `M ${start.rightX} ${start.rightY} C ${cp1x} ${start.rightY}, ${cp2x} ${end.leftY}, ${end.leftX} ${end.leftY}`;
		}
		return `M ${start.rightX} ${start.rightY} C ${start.rightX + cpDist} ${start.rightY}, ${end.leftX - cpDist} ${end.leftY}, ${end.leftX} ${end.leftY}`;
	}
</script>

<div class="topology-page">
	<header>
		<h1>Pipeline Topology</h1>
		<p>Complex back-loop routing optimized for clear data path visualization</p>
	</header>

	<div class="canvas-container">
		<svg width="100%" height="100%" viewBox="0 0 1300 520" preserveAspectRatio="xMidYMid meet">
			<defs>
				<marker id="arrowhead" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto">
					<polygon points="0 0, 10 4, 0 8" fill="#94a3b8" />
				</marker>
			</defs>

			<!-- 1. Nodes -->
			{#each nodes as node}
				{@const w = node.type === 'service' ? nodeW : topicW}
				{@const h = node.type === 'service' ? nodeH : topicH}
				<g transform="translate({node.x}, {node.y})">
					<rect 
						x="0" y="0" width={w} height={h} 
						rx={node.type === 'service' ? "8" : "22"} ry={node.type === 'service' ? "8" : "22"}
						fill="white"
						stroke={node.type === 'service' ? (node.status === 'online' ? '#0ea5e9' : '#ef4444') : '#94a3b8'}
						stroke-width="2.5"
						class="node-rect {node.type}"
					/>
					<foreignObject x="0" y="0" width={w} height={h}>
						<div class="node-content {node.type}">
							<div class="icon-box">
								<svelte:component this={node.icon} size={18} />
							</div>
							<div class="text-box">
								<span class="name">{node.name}</span>
							</div>
						</div>
					</foreignObject>
				</g>
			{/each}

			<!-- 2. Connections -->
			{#each connections as conn}
				{@const path = getPath(conn)}
				<path
					d={path}
					stroke="#0ea5e9"
					stroke-width="2.5"
					stroke-opacity="0.4"
					fill="none"
					marker-end="url(#arrowhead)"
					style="pointer-events: none;"
				/>
				<circle r="4" fill="#0ea5e9">
					<animateMotion 
						dur={conn.id === 'c13' ? '8s' : conn.id === 'c10' ? '6s' : '5s'} 
						repeatCount="indefinite" path={path} 
					/>
				</circle>
			{/each}
		</svg>
	</div>

	<div class="legend">
		<div class="legend-item"><span class="box service"></span> Service</div>
		<div class="legend-item"><span class="box topic"></span> Kafka Topic</div>
		<div class="legend-item"><span class="line"></span> Stream Path</div>
	</div>
</div>

<style>
	header { margin-bottom: 24px; }
	header h1 { margin: 0; font-size: 1.75rem; color: #0f172a; }
	header p { margin: 4px 0 0 0; color: #64748b; font-size: 0.9rem; }

	.canvas-container {
		background: #ffffff;
		border-radius: 16px;
		padding: 40px;
		border: 1px solid #e2e8f0;
		display: flex;
		justify-content: center;
		align-items: center;
		height: 550px;
		box-shadow: inset 0 2px 6px rgba(0,0,0,0.02);
	}

	svg { width: 100%; height: 100%; max-width: 1300px; overflow: visible; }

	.node-rect { filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.05)); }
	.node-rect.topic { fill: #fcfcfc; stroke-dasharray: 5 3; }

	.node-content { display: flex; align-items: center; padding: 0 12px; height: 100%; gap: 10px; pointer-events: none; }
	.icon-box { color: #64748b; display: flex; align-items: center; }
	.node-rect.service + foreignObject .icon-box { color: #0ea5e9; }

	.text-box { display: flex; flex-direction: column; min-width: 0; }
	.name { font-weight: 700; font-size: 0.75rem; color: #1e293b; line-height: 1.2; word-break: break-all; }
	.topic .name { font-family: 'JetBrains Mono', monospace; font-size: 0.65rem; color: #475569; text-align: center; width: 100%; }

	.legend {
		margin-top: 32px;
		display: flex;
		gap: 32px;
		justify-content: center;
		background: white;
		padding: 12px 32px;
		border-radius: 999px;
		border: 1px solid #e2e8f0;
	}

	.legend-item { display: flex; align-items: center; gap: 8px; font-size: 0.85rem; font-weight: 600; color: #475569; }
	.box { width: 12px; height: 12px; border-radius: 3px; border: 1.5px solid; }
	.box.service { background: #f0f9ff; border-color: #0ea5e9; }
	.box.topic { background: #f8fafc; border-color: #94a3b8; border-style: dashed; }
	.line { width: 20px; height: 2px; background: #0ea5e9; opacity: 0.4; }
</style>
