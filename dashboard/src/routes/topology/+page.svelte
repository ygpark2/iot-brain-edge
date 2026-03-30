<script>
	import { Cpu, Database, Share2, Zap, Activity } from 'lucide-svelte';

	const nodes = [
		{ id: 'edge', name: 'Edge Agent', status: 'online', icon: Cpu, x: 50, y: 150 },
		{ id: 'kafka', name: 'Kafka Cluster', status: 'online', icon: Share2, x: 300, y: 150 },
		{ id: 'flink', name: 'Flink Jobs', status: 'online', icon: Zap, x: 550, y: 150 },
		{ id: 'inference', name: 'Inference Svc', status: 'offline', icon: Activity, x: 800, y: 50 },
		{ id: 'clickhouse', name: 'ClickHouse', status: 'online', icon: Database, x: 800, y: 250 }
	];

	// 노드 크기 정의
	const nodeW = 160;
	const nodeH = 80;

	const connections = [
		{ from: 'edge', to: 'kafka', type: 'straight' },
		{ from: 'kafka', to: 'flink', type: 'straight' },
		{ from: 'flink', to: 'inference', type: 'curve-up' },
		{ from: 'inference', to: 'kafka', type: 'back-loop' },
		{ from: 'flink', to: 'clickhouse', type: 'curve-down' },
		{ from: 'kafka', to: 'clickhouse', type: 'wide-loop' }
	];

	function getPath(conn) {
		const startNode = nodes.find(n => n.id === conn.from);
		const endNode = nodes.find(n => n.id === conn.to);
		
		const x1 = startNode.x + nodeW;
		const y1 = startNode.y + nodeH / 2;
		const x2 = endNode.x;
		const y2 = endNode.y + nodeH / 2;

		if (conn.type === 'straight') {
			return `M ${x1} ${y1} L ${x2} ${y2}`;
		}
		if (conn.type === 'curve-up' || conn.type === 'curve-down') {
			const cp1x = x1 + (x2 - x1) / 2;
			return `M ${x1} ${y1} C ${cp1x} ${y1}, ${cp1x} ${y2}, ${x2} ${y2}`;
		}
		if (conn.type === 'back-loop') {
			// Inference에서 Kafka로 돌아오는 선 (위로 돌아감)
			return `M ${startNode.x} ${y1} C ${startNode.x - 100} ${y1 - 100}, ${endNode.x + nodeW + 100} ${endNode.y - 100}, ${endNode.x + nodeW / 2} ${endNode.y}`;
		}
		if (conn.type === 'wide-loop') {
			// Kafka에서 ClickHouse로 가는 선 (아래로 크게 돌아감)
			return `M ${x1 - 20} ${startNode.y + nodeH} C ${x1} ${startNode.y + nodeH + 80}, ${x2} ${y2 + 80}, ${x2} ${y2}`;
		}
		return `M ${x1} ${y1} L ${x2} ${y2}`;
	}
</script>

<div class="topology-page">
	<header>
		<h1>Pipeline Topology</h1>
		<p>Enhanced visualization with precise path routing and clear node borders</p>
	</header>

	<div class="canvas-container">
		<svg width="1000" height="450" viewBox="0 0 1000 450">
			<defs>
				<marker id="arrowhead" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto">
					<polygon points="0 0, 10 4, 0 8" fill="#475569" />
				</marker>
				<!-- Glow Filter for Particles -->
				<filter id="glow" x="-50%" y="-50%" width="200%" height="200%">
					<feGaussianBlur stdDeviation="2.5" result="coloredBlur"/>
					<feMerge>
						<feMergeNode in="coloredBlur"/>
						<feMergeNode in="SourceGraphic"/>
					</feMerge>
				</filter>
			</defs>

			<!-- 1. Connections -->
			{#each connections as conn}
				{@const path = getPath(conn)}
				{@const sourceNode = nodes.find(n => n.id === conn.from)}
				<path
					d={path}
					stroke="#94a3b8"
					stroke-width="3"
					fill="none"
					marker-end="url(#arrowhead)"
				/>
				<!-- Flow Animation -->
				{#if sourceNode.status === 'online'}
					<circle r="5" fill="#0ea5e9" filter="url(#glow)">
						<animateMotion dur="3s" repeatCount="indefinite" path={path} />
					</circle>
				{/if}
			{/each}

			<!-- 2. Nodes -->
			{#each nodes as node}
				<g transform="translate({node.x}, {node.y})">
					<!-- Border (Using SVG rect to prevent clipping) -->
					<rect 
						x="0" y="0" width={nodeW} height={nodeH} 
						rx="12" ry="12"
						fill="white"
						stroke={node.status === 'online' ? '#0ea5e9' : '#ef4444'}
						stroke-width="3"
						class="node-rect {node.status}"
					/>
					
					<!-- Content -->
					<foreignObject x="0" y="0" width={nodeW} height={nodeH}>
						<div class="node-content {node.status}">
							<div class="icon-box">
								<svelte:component this={node.icon} size={24} />
							</div>
							<div class="text-box">
								<span class="name">{node.name}</span>
								<span class="status-tag">{node.status}</span>
							</div>
						</div>
					</foreignObject>
				</g>
			{/each}
		</svg>
	</div>

	<div class="legend">
		<div class="legend-item"><span class="box online"></span> Online</div>
		<div class="legend-item"><span class="box offline"></span> Offline</div>
		<div class="legend-item"><span class="line"></span> Data Path</div>
		<div class="legend-item"><span class="particle"></span> Active Stream</div>
	</div>
</div>

<style>
	header { margin-bottom: 40px; }
	header h1 { margin: 0; font-size: 1.875rem; color: #0f172a; }
	header p { margin: 4px 0 0 0; color: #64748b; }

	.canvas-container {
		background: #f8fafc;
		border-radius: 20px;
		padding: 40px;
		display: flex;
		justify-content: center;
		border: 1px solid #e2e8f0;
		box-shadow: inset 0 2px 4px 0 rgba(0, 0, 0, 0.05);
	}

	svg { overflow: visible; }

	.node-rect {
		filter: drop-shadow(0 4px 6px rgba(0, 0, 0, 0.05));
		transition: all 0.3s;
	}

	.node-rect.online {
		fill: #f0f9ff;
	}

	.node-rect.offline {
		fill: #fef2f2;
	}

	.node-content {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 12px;
		height: 100%;
		padding: 0 16px;
		pointer-events: none;
	}

	.icon-box {
		color: #64748b;
		display: flex;
		align-items: center;
	}

	.node-content.online .icon-box { color: #0ea5e9; }
	.node-content.offline .icon-box { color: #ef4444; }

	.text-box {
		display: flex;
		flex-direction: column;
	}

	.name {
		font-weight: 700;
		font-size: 0.875rem;
		color: #1e293b;
	}

	.status-tag {
		font-size: 0.7rem;
		text-transform: uppercase;
		font-weight: 800;
		letter-spacing: 0.025em;
	}

	.node-content.online .status-tag { color: #10b981; }
	.node-content.offline .status-tag { color: #ef4444; }

	/* Legend */
	.legend {
		margin-top: 32px;
		display: flex;
		gap: 24px;
		justify-content: center;
		background: white;
		padding: 16px 32px;
		border-radius: 9999px;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
		border: 1px solid #e2e8f0;
	}

	.legend-item {
		display: flex;
		align-items: center;
		gap: 8px;
		font-size: 0.875rem;
		font-weight: 600;
		color: #475569;
	}

	.box { width: 14px; height: 14px; border-radius: 4px; border: 2px solid; }
	.box.online { background: #f0f9ff; border-color: #0ea5e9; }
	.box.offline { background: #fef2f2; border-color: #ef4444; }

	.line { width: 24px; height: 3px; background: #94a3b8; border-radius: 2px; }
	.particle { 
		width: 8px; height: 8px; background: #0ea5e9; border-radius: 50%; 
		box-shadow: 0 0 8px #0ea5e9;
	}
</style>
