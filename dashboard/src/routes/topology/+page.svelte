<script>
	import { Server, Cpu, Database, Share2, Zap, ArrowRight } from 'lucide-svelte';

	const nodes = [
		{ id: 'edge', name: 'Edge Agent', type: 'source', status: 'online', icon: Cpu, x: 50, y: 150 },
		{ id: 'kafka', name: 'Kafka Cluster', type: 'broker', status: 'online', icon: Share2, x: 300, y: 150 },
		{ id: 'flink', name: 'Flink Jobs', type: 'processor', status: 'online', icon: Zap, x: 550, y: 150 },
		{ id: 'inference', name: 'Inference Svc', type: 'processor', status: 'offline', icon: Activity, x: 800, y: 50 },
		{ id: 'clickhouse', name: 'ClickHouse', type: 'sink', status: 'online', icon: Database, x: 800, y: 250 }
	];

	import { Activity } from 'lucide-svelte';

	const connections = [
		{ from: 'edge', to: 'kafka', label: 'Raw Frames' },
		{ from: 'kafka', to: 'flink', label: 'Stream' },
		{ from: 'flink', to: 'inference', label: 'Requests' },
		{ from: 'inference', to: 'kafka', label: 'Results' },
		{ from: 'flink', to: 'clickhouse', label: 'Ingest' },
		{ from: 'kafka', to: 'clickhouse', label: 'Persistence' }
	];

	function getPos(id) {
		const node = nodes.find((n) => n.id === id);
		return node ? { x: node.x + 80, y: node.y + 40 } : { x: 0, y: 0 };
	}
</script>

<div class="topology-page">
	<header>
		<h1>Pipeline Topology</h1>
		<p>Real-time visualization of data flow and service connectivity</p>
	</header>

	<div class="canvas-container">
		<svg width="1000" height="400" viewBox="0 0 1000 400">
			<!-- Connections -->
			<defs>
				<marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
					<polygon points="0 0, 10 3.5, 0 7" fill="#94a3b8" />
				</marker>
			</defs>

			{#each connections as conn}
				{@const start = getPos(conn.from)}
				{@const end = getPos(conn.to)}
				<path
					d="M {start.x} {start.y} L {end.x - 80} {end.y}"
					stroke="#e2e8f0"
					stroke-width="2"
					fill="none"
					marker-end="url(#arrowhead)"
				/>
				<!-- Flow Animation Particle -->
				{#if nodes.find((n) => n.id === conn.from).status === 'online'}
					<circle r="4" fill="#38bdf8" class="flow-particle">
						<animateMotion
							dur="3s"
							repeatCount="indefinite"
							path="M {start.x} {start.y} L {end.x - 80} {end.y}"
						/>
					</circle>
				{if}
			{/each}

			<!-- Nodes -->
			{#each nodes as node}
				<foreignObject x={node.x} y={node.y} width="160" height="100">
					<div class="node {node.status}">
						<div class="node-icon">
							<svelte:component this={node.icon} size={24} />
						</div>
						<div class="node-info">
							<span class="name">{node.name}</span>
							<span class="status-text">{node.status}</span>
						</div>
					</div>
				</foreignObject>
			{/each}
		</svg>
	</div>

	<div class="legend">
		<div class="legend-item"><span class="dot online"></span> Online</div>
		<div class="legend-item"><span class="dot offline"></span> Offline</div>
		<div class="legend-item"><span class="line"></span> Data Path</div>
		<div class="legend-item"><span class="particle"></span> Active Flow</div>
	</div>
</div>

<style>
	header {
		margin-bottom: 40px;
	}

	header h1 {
		margin: 0;
		font-size: 1.875rem;
		color: #0f172a;
	}

	header p {
		margin: 4px 0 0 0;
		color: #64748b;
	}

	.canvas-container {
		background: white;
		border-radius: 16px;
		box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
		padding: 40px;
		overflow: auto;
		display: flex;
		justify-content: center;
	}

	.node {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		background: #f8fafc;
		border: 2px solid #e2e8f0;
		border-radius: 12px;
		padding: 12px;
		width: 140px;
		height: 80px;
		transition: all 0.3s;
	}

	.node.online {
		border-color: #38bdf8;
		background: #f0f9ff;
	}

	.node.offline {
		border-color: #ef4444;
		opacity: 0.7;
	}

	.node-icon {
		margin-bottom: 8px;
		color: #64748b;
	}

	.node.online .node-icon {
		color: #0ea5e9;
	}

	.node .name {
		font-size: 0.875rem;
		font-weight: 600;
		color: #1e293b;
	}

	.node .status-text {
		font-size: 0.75rem;
		text-transform: uppercase;
		font-weight: 700;
		color: #94a3b8;
	}

	.node.online .status-text {
		color: #10b981;
	}

	.node.offline .status-text {
		color: #ef4444;
	}

	.flow-particle {
		filter: drop-shadow(0 0 4px #38bdf8);
	}

	.legend {
		margin-top: 32px;
		display: flex;
		gap: 24px;
		justify-content: center;
		background: white;
		padding: 16px;
		border-radius: 12px;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
	}

	.legend-item {
		display: flex;
		align-items: center;
		gap: 8px;
		font-size: 0.875rem;
		color: #64748b;
	}

	.dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
	}

	.dot.online {
		background-color: #10b981;
	}

	.dot.offline {
		background-color: #ef4444;
	}

	.line {
		width: 24px;
		height: 2px;
		background-color: #e2e8f0;
	}

	.particle {
		width: 8px;
		height: 8px;
		background-color: #38bdf8;
		border-radius: 50%;
	}
</style>
