<script>
	import { Search, Clock, CheckCircle2, AlertCircle, MapPin } from 'lucide-svelte';

	let searchQuery = '';
	let searchResult = null;

	function handleSearch() {
		if (!searchQuery) return;
		// Simulated search result
		searchResult = {
			sessionId: searchQuery,
			deviceId: 'device-001',
			startTime: new Date(Date.now() - 5000).toISOString(),
			status: 'Completed',
			timeline: [
				{ stage: 'Edge Agent', action: 'Data Ingested', ts: new Date(Date.now() - 5000).toISOString(), status: 'success' },
				{ stage: 'Kafka', action: 'Distributed to rawframes', ts: new Date(Date.now() - 4800).toISOString(), status: 'success' },
				{ stage: 'Flink', action: 'Window Aggregated', ts: new Date(Date.now() - 3000).toISOString(), status: 'success' },
				{ stage: 'Inference', action: 'Prediction: Normal (0.12)', ts: new Date(Date.now() - 2500).toISOString(), status: 'success' },
				{ stage: 'ClickHouse', action: 'Stored in session_features', ts: new Date(Date.now() - 1000).toISOString(), status: 'success' }
			]
		};
	}
</script>

<div class="trace-page">
	<header>
		<h1>Trace Timeline</h1>
		<p>Track the end-to-end journey of a specific session</p>
	</header>

	<div class="search-section">
		<div class="search-box">
			<Search size={20} class="search-icon" />
			<input
				type="text"
				placeholder="Enter Session ID (e.g. sess-abc123)..."
				bind:value={searchQuery}
				on:keydown={(e) => e.key === 'Enter' && handleSearch()}
			/>
			<button class="btn-primary" on:click={handleSearch}>Trace</button>
		</div>
	</div>

	{#if searchResult}
		<div class="result-container fade-in">
			<div class="result-header">
				<div class="info-group">
					<span class="label">Session ID</span>
					<span class="value">{searchResult.sessionId}</span>
				</div>
				<div class="info-group">
					<span class="label">Device</span>
					<span class="value">{searchResult.deviceId}</span>
				</div>
				<div class="info-group">
					<span class="label">Status</span>
					<span class="badge {searchResult.status.toLowerCase()}">{searchResult.status}</span>
				</div>
			</div>

			<div class="timeline">
				{#each searchResult.timeline as step, i}
					<div class="timeline-item">
						<div class="timeline-marker">
							<div class="dot {step.status}"></div>
							{#if i < searchResult.timeline.length - 1}
								<div class="line"></div>
							{/if}
						</div>
						<div class="timeline-content">
							<div class="time">{new Date(step.ts).toLocaleTimeString()}</div>
							<div class="stage">{step.stage}</div>
							<div class="action">{step.action}</div>
						</div>
					</div>
				{/each}
			</div>
		</div>
	{:else if searchQuery}
		<div class="empty-state">
			<Clock size={48} />
			<h3>No trace found</h3>
			<p>Try searching for a valid Session ID to see its journey.</p>
		</div>
	{/if}
</div>

<style>
	header { margin-bottom: 32px; }
	header h1 { margin: 0; font-size: 1.875rem; color: #0f172a; }

	.search-section {
		background: white;
		padding: 32px;
		border-radius: 12px;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
		margin-bottom: 32px;
	}

	.search-box {
		display: flex;
		gap: 12px;
		max-width: 600px;
		margin: 0 auto;
		position: relative;
	}

	.search-icon {
		position: absolute;
		left: 16px;
		top: 50%;
		transform: translateY(-50%);
		color: #94a3b8;
	}

	.search-box input {
		flex: 1;
		padding: 12px 12px 12px 48px;
		border: 1px solid #e2e8f0;
		border-radius: 8px;
		outline: none;
		font-size: 1rem;
	}

	.search-box input:focus { border-color: #0ea5e9; }

	.btn-primary {
		background: #0ea5e9;
		color: white;
		border: none;
		padding: 0 24px;
		border-radius: 8px;
		font-weight: 600;
		cursor: pointer;
	}

	.result-container {
		background: white;
		border-radius: 12px;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
		padding: 32px;
	}

	.result-header {
		display: flex;
		gap: 40px;
		padding-bottom: 24px;
		border-bottom: 1px solid #f1f5f9;
		margin-bottom: 32px;
	}

	.info-group .label {
		display: block;
		font-size: 0.75rem;
		color: #64748b;
		text-transform: uppercase;
		margin-bottom: 4px;
	}

	.info-group .value { font-weight: 600; color: #1e293b; }

	.badge {
		padding: 4px 12px;
		border-radius: 9999px;
		font-size: 0.75rem;
		font-weight: 600;
	}

	.badge.completed { background: #dcfce7; color: #166534; }

	/* Timeline */
	.timeline { padding-left: 20px; }

	.timeline-item {
		display: flex;
		gap: 24px;
		padding-bottom: 32px;
	}

	.timeline-marker {
		display: flex;
		flex-direction: column;
		align-items: center;
		position: relative;
	}

	.dot {
		width: 12px;
		height: 12px;
		border-radius: 50%;
		background: #cbd5e1;
		z-index: 1;
	}

	.dot.success { background: #10b981; box-shadow: 0 0 0 4px #dcfce7; }

	.line {
		width: 2px;
		flex: 1;
		background: #e2e8f0;
		position: absolute;
		top: 12px;
		bottom: -20px;
	}

	.timeline-content .time { font-size: 0.75rem; color: #94a3b8; font-family: monospace; }
	.timeline-content .stage { font-weight: 600; color: #0f172a; margin: 4px 0; }
	.timeline-content .action { font-size: 0.875rem; color: #64748b; }

	.fade-in { animation: fadeIn 0.4s ease-out; }
	@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

	.empty-state {
		text-align: center;
		padding: 64px;
		color: #94a3b8;
	}
</style>
