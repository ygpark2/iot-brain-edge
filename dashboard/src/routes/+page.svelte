<script>
	import { onMount } from 'svelte';
	import { Activity, Database, Zap, AlertTriangle } from 'lucide-svelte';
	import Chart from 'chart.js/auto';

	let throughputChart;
	let chartCanvas;

	const metrics = [
		{ name: 'Total Events', value: '1.2M', icon: Activity, color: '#0ea5e9' },
		{ name: 'Active Devices', value: '42', icon: Zap, color: '#f59e0b' },
		{ name: 'DB Storage', value: '850 GB', icon: Database, color: '#10b981' },
		{ name: 'Alerts (24h)', value: '12', icon: AlertTriangle, color: '#ef4444' }
	];

	onMount(() => {
		const ctx = chartCanvas.getContext('2d');
		throughputChart = new Chart(ctx, {
			type: 'line',
			data: {
				labels: Array.from({ length: 20 }, (_, i) => i + 's ago').reverse(),
				datasets: [
					{
						label: 'Events / sec',
						data: Array.from({ length: 20 }, () => Math.floor(Math.random() * 100) + 500),
						borderColor: '#0ea5e9',
						tension: 0.4,
						fill: true,
						backgroundColor: 'rgba(14, 165, 233, 0.1)'
					}
				]
			},
			options: {
				responsive: true,
				maintainAspectRatio: false,
				plugins: { legend: { display: false } },
				scales: {
					y: { beginAtZero: true, grid: { color: '#f1f5f9' } },
					x: { grid: { display: false } }
				}
			}
		});

		const interval = setInterval(() => {
			throughputChart.data.datasets[0].data.shift();
			throughputChart.data.datasets[0].data.push(Math.floor(Math.random() * 100) + 500);
			throughputChart.update();
		}, 1000);

		return () => clearInterval(interval);
	});
</script>

<div class="dashboard-home">
	<header>
		<h1>System Overview</h1>
		<p>Real-time pipeline monitoring and statistics</p>
	</header>

	<div class="metrics-grid">
		{#each metrics as metric}
			<div class="metric-card">
				<div class="metric-icon" style="background-color: {metric.color}20; color: {metric.color}">
					<svelte:component this={metric.icon} size={24} />
				</div>
				<div class="metric-info">
					<span class="label">{metric.name}</span>
					<span class="value">{metric.value}</span>
				</div>
			</div>
		{/each}
	</div>

	<div class="chart-container">
		<div class="chart-header">
			<h3>Throughput (Events / sec)</h3>
			<span class="live-indicator"><span class="dot"></span> LIVE</span>
		</div>
		<div class="canvas-wrapper">
			<canvas bind:this={chartCanvas}></canvas>
		</div>
	</div>

	<section class="pipeline-status">
		<h3>Service Health</h3>
		<div class="status-list">
			<div class="status-item">
				<span class="service">Edge Agent</span>
				<span class="badge online">Online</span>
			</div>
			<div class="status-item">
				<span class="service">Kafka Broker</span>
				<span class="badge online">Online</span>
			</div>
			<div class="status-item">
				<span class="service">Flink JobManager</span>
				<span class="badge online">Online</span>
			</div>
			<div class="status-item">
				<span class="service">Inference Service</span>
				<span class="badge offline">Offline</span>
			</div>
		</div>
	</section>
</div>

<style>
	header {
		margin-bottom: 32px;
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

	.metrics-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
		gap: 24px;
		margin-bottom: 32px;
	}

	.metric-card {
		background: white;
		padding: 24px;
		border-radius: 12px;
		display: flex;
		align-items: center;
		gap: 20px;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
	}

	.metric-icon {
		padding: 12px;
		border-radius: 10px;
	}

	.metric-info {
		display: flex;
		flex-direction: column;
	}

	.metric-info .label {
		font-size: 0.875rem;
		color: #64748b;
	}

	.metric-info .value {
		font-size: 1.5rem;
		font-weight: 600;
		color: #0f172a;
	}

	.chart-container {
		background: white;
		padding: 24px;
		border-radius: 12px;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
		margin-bottom: 32px;
	}

	.chart-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 20px;
	}

	.chart-header h3 {
		margin: 0;
		font-size: 1.125rem;
	}

	.live-indicator {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 0.75rem;
		font-weight: 600;
		color: #ef4444;
	}

	.dot {
		width: 8px;
		height: 8px;
		background-color: #ef4444;
		border-radius: 50%;
		animation: pulse 2s infinite;
	}

	@keyframes pulse {
		0% {
			transform: scale(0.95);
			box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.7);
		}
		70% {
			transform: scale(1);
			box-shadow: 0 0 0 6px rgba(239, 68, 68, 0);
		}
		100% {
			transform: scale(0.95);
			box-shadow: 0 0 0 0 rgba(239, 68, 68, 0);
		}
	}

	.canvas-wrapper {
		height: 300px;
	}

	.pipeline-status {
		background: white;
		padding: 24px;
		border-radius: 12px;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
	}

	.status-list {
		display: flex;
		flex-direction: column;
		gap: 16px;
	}

	.status-item {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding-bottom: 12px;
		border-bottom: 1px solid #f1f5f9;
	}

	.status-item:last-child {
		border-bottom: none;
		padding-bottom: 0;
	}

	.badge {
		padding: 4px 12px;
		border-radius: 9999px;
		font-size: 0.75rem;
		font-weight: 600;
	}

	.badge.online {
		background-color: #dcfce7;
		color: #166534;
	}

	.badge.offline {
		background-color: #fee2e2;
		color: #991b1b;
	}
</style>
