<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { Activity, Database, Zap, AlertTriangle } from 'lucide-svelte';
	import Chart from 'chart.js/auto';

	let throughputChart: Chart;
	let chartCanvas: HTMLCanvasElement;
	let throughputInterval: ReturnType<typeof setInterval> | undefined;
	let metricsInterval: ReturnType<typeof setInterval> | undefined;
	let error = '';

	let stats = {
		totalEvents: 0,
		activeDevices: 0,
		storageBytes: 0,
		alerts24h: 0
	};

	$: storageGB = `${(stats.storageBytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;

	$: metrics = [
		{ name: 'Total Events', value: stats.totalEvents.toLocaleString(), icon: Activity, color: '#0ea5e9' },
		{ name: 'Active Devices', value: stats.activeDevices, icon: Zap, color: '#f59e0b' },
		{ name: 'DB Storage', value: storageGB, icon: Database, color: '#10b981' },
		{ name: 'Alerts (24h)', value: stats.alerts24h, icon: AlertTriangle, color: '#ef4444' }
	];

	async function loadMetrics() {
		try {
			const res = await fetch('/api/dashboard/metrics');
			const json = await res.json();
			if (!res.ok) throw new Error(json.error || 'Failed to load metrics');
			stats = json;
			error = '';
		} catch (e) {
			error = String(e);
		}
	}

	async function fetchThroughput() {
		try {
			const res = await fetch('/api/throughput');
			if (!res.ok) return 0;
			const json = await res.json();
			return json.count || 0;
		} catch {
			return 0;
		}
	}

	onMount(() => {
		if (!chartCanvas) return;

		const ctx = chartCanvas.getContext('2d');
		if (!ctx) return;

		throughputChart = new Chart(ctx, {
			type: 'line',
			data: {
				labels: Array.from({ length: 30 }, () => ''),
				datasets: [
					{
						label: 'Events / sec',
						data: Array.from({ length: 30 }, () => 0),
						borderColor: '#0ea5e9',
						borderWidth: 2,
						pointRadius: 0,
						tension: 0.4,
						fill: true,
						backgroundColor: 'rgba(14, 165, 233, 0.1)'
					}
				]
			},
			options: {
				responsive: true,
				maintainAspectRatio: false,
				animation: { duration: 0 },
				plugins: { legend: { display: false } },
				scales: {
					y: {
						beginAtZero: true,
						suggestedMax: 10,
						grid: { color: '#f1f5f9' },
						ticks: { font: { size: 10 } }
					},
					x: { display: false }
				}
			}
		});

		void loadMetrics();
		metricsInterval = setInterval(() => {
			void loadMetrics();
		}, 10000);

		throughputInterval = setInterval(async () => {
			const count = await fetchThroughput();
			if (throughputChart) {
				throughputChart.data.datasets[0].data.shift();
				throughputChart.data.datasets[0].data.push(count);
				throughputChart.update('none');
			}
		}, 1000);
	});

	onDestroy(() => {
		if (throughputInterval) clearInterval(throughputInterval);
		if (metricsInterval) clearInterval(metricsInterval);
		if (throughputChart) throughputChart.destroy();
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

	{#if error}
		<p class="error-banner">{error}</p>
	{/if}

	<div class="chart-container">
		<div class="chart-header">
			<h3>Throughput (Events / sec)</h3>
			<div class="live-indicator">
				<div class="dot"></div>
				<span>LIVE</span>
			</div>
		</div>
		<div class="canvas-wrapper">
			<canvas bind:this={chartCanvas}></canvas>
		</div>
	</div>

	<section class="pipeline-status">
		<h3>Service Health</h3>
		<div class="status-list">
			{#each ['Edge Agent', 'Kafka Broker', 'Flink Cluster', 'Inference Service'] as service}
				<div class="status-item">
					<span class="service">{service}</span>
					<span class="badge online">Online</span>
				</div>
			{/each}
		</div>
	</section>
</div>

<style>
	header { margin-bottom: 32px; }
	header h1 { margin: 0; font-size: 1.875rem; color: #0f172a; font-weight: 800; }
	header p { margin: 4px 0 0 0; color: #64748b; }

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
		border: 1px solid #f1f5f9;
		box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
	}

	.metric-icon { padding: 12px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
	.metric-info { display: flex; flex-direction: column; }
	.metric-info .label { font-size: 0.875rem; color: #64748b; font-weight: 500; }
	.metric-info .value { font-size: 1.5rem; font-weight: 700; color: #0f172a; }

	.chart-container {
		background: white;
		padding: 24px;
		border-radius: 16px;
		border: 1px solid #f1f5f9;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
		margin-bottom: 32px;
	}

	.chart-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
	.chart-header h3 { margin: 0; font-size: 1.125rem; font-weight: 700; color: #1e293b; }

	.live-indicator {
		display: flex;
		align-items: center;
		gap: 8px;
		background: #ecfdf5;
		padding: 4px 12px;
		border-radius: 9999px;
		color: #059669;
		font-size: 0.75rem;
		font-weight: 700;
	}

	.dot { width: 8px; height: 8px; background-color: #10b981; border-radius: 50%; }

	.canvas-wrapper { height: 320px; width: 100%; position: relative; }
	canvas { width: 100% !important; height: 100% !important; }

	.pipeline-status { background: white; padding: 24px; border-radius: 16px; border: 1px solid #f1f5f9; }
	.pipeline-status h3 { margin-top: 0; margin-bottom: 20px; font-size: 1.125rem; font-weight: 700; }
	.status-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; }
	.status-item { 
		display: flex; justify-content: space-between; align-items: center; 
		padding: 12px 16px; background: #f8fafc; border-radius: 8px;
	}
	.service { font-weight: 600; color: #475569; font-size: 0.875rem; }
	.badge { padding: 4px 10px; border-radius: 6px; font-size: 0.7rem; font-weight: 800; text-transform: uppercase; }
	.badge.online { background-color: #dcfce7; color: #166534; }
	.error-banner { color: #b91c1c; font-size: 0.875rem; margin: 0 0 24px 0; }
</style>
