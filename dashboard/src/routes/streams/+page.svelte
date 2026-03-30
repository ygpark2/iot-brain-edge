<script>
	import { Search, Eye, Filter, RefreshCcw } from 'lucide-svelte';
	import { onMount } from 'svelte';

	let messages = [
		{ id: 'm1', topic: 'rawframes', type: 'RawFrame', device: 'device-001', ts: new Date().toISOString(), size: '1.2KB' },
		{ id: 'm2', topic: 'inference-requests', type: 'InferenceRequest', device: 'device-001', ts: new Date().toISOString(), size: '0.5KB' },
		{ id: 'm3', topic: 'inference-results', type: 'InferenceResult', device: 'device-001', ts: new Date().toISOString(), size: '0.8KB' }
	];

	let selectedMessage = null;
	let isStreaming = true;

	onMount(() => {
		const interval = setInterval(() => {
			if (!isStreaming) return;
			const newMsg = {
				id: Math.random().toString(36).substr(2, 9),
				topic: ['rawframes', 'inference-requests', 'inference-results', 'inference-alerts'][Math.floor(Math.random() * 4)],
				type: 'EventEnvelope',
				device: 'device-' + Math.floor(Math.random() * 10).toString().padStart(3, '0'),
				ts: new Date().toISOString(),
				size: (Math.random() * 2).toFixed(1) + 'KB'
			};
			messages = [newMsg, ...messages.slice(0, 19)];
		}, 2000);
		return () => clearInterval(interval);
	});
</script>

<div class="streams-page">
	<header>
		<div class="title-section">
			<h1>Message Inspector</h1>
			<p>Real-time sampling of Kafka data streams</p>
		</div>
		<div class="controls">
			<button class="btn-secondary" on:click={() => (isStreaming = !isStreaming)}>
				<RefreshCcw size={18} class={isStreaming ? 'spin' : ''} />
				{isStreaming ? 'Pause Stream' : 'Resume Stream'}
			</button>
		</div>
	</header>

	<div class="filters">
		<div class="search-wrapper">
			<Search size={18} class="search-icon" />
			<input type="text" placeholder="Filter by Device ID or Topic..." />
		</div>
		<select>
			<option>All Topics</option>
			<option>rawframes</option>
			<option>inference-requests</option>
			<option>inference-results</option>
		</select>
	</div>

	<div class="table-container">
		<table>
			<thead>
				<tr>
					<th>Timestamp</th>
					<th>Topic</th>
					<th>Event Type</th>
					<th>Device ID</th>
					<th>Size</th>
					<th>Action</th>
				</tr>
			</thead>
			<tbody>
				{#each messages as msg (msg.id)}
					<tr class="fade-in">
						<td class="ts">{new Date(msg.ts).toLocaleTimeString()}</td>
						<td><span class="topic-tag">{msg.topic}</span></td>
						<td>{msg.type}</td>
						<td><span class="device-id">{msg.device}</span></td>
						<td>{msg.size}</td>
						<td>
							<button class="btn-icon" on:click={() => (selectedMessage = msg)}>
								<Eye size={16} />
							</button>
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>

	{#if selectedMessage}
		<div class="modal-overlay" on:click={() => (selectedMessage = null)}>
			<div class="modal" on:click|stopPropagation>
				<div class="modal-header">
					<h3>Message Detail: {selectedMessage.id}</h3>
					<button class="btn-close" on:click={() => (selectedMessage = null)}>×</button>
				</div>
				<div class="json-viewer">
					<pre>
{JSON.stringify(
	{
		event_id: selectedMessage.id,
		schema_version: 'v1',
		event_type: selectedMessage.type,
		device_id: selectedMessage.device,
		sensor_type: 'GRF',
		session_id: 'session-' + Math.random().toString(36).substr(2, 5),
		sensor_timestamp_ms: Date.now(),
		payload: {
			value: (Math.random() * 100).toFixed(2),
			unit: 'kgf',
			coordinates: [12.5, 45.2]
		}
	},
	null,
	2
)}
					</pre>
				</div>
			</div>
		</div>
	{/if}
</div>

<style>
	header {
		display: flex;
		justify-content: space-between;
		align-items: flex-end;
		margin-bottom: 32px;
	}

	header h1 {
		margin: 0;
		font-size: 1.875rem;
		color: #0f172a;
	}

	.btn-secondary {
		display: flex;
		align-items: center;
		gap: 8px;
		background: white;
		border: 1px solid #e2e8f0;
		padding: 10px 16px;
		border-radius: 8px;
		cursor: pointer;
		font-weight: 600;
	}

	.spin {
		animation: spin 2s linear infinite;
	}

	@keyframes spin {
		from { transform: rotate(0deg); }
		to { transform: rotate(360deg); }
	}

	.filters {
		display: flex;
		gap: 16px;
		margin-bottom: 24px;
	}

	.search-wrapper {
		position: relative;
		flex: 1;
	}

	.search-icon {
		position: absolute;
		left: 12px;
		top: 50%;
		transform: translateY(-50%);
		color: #94a3b8;
	}

	.search-wrapper input, .filters select {
		width: 100%;
		padding: 10px 12px 10px 40px;
		border: 1px solid #e2e8f0;
		border-radius: 8px;
		background: white;
	}

	.filters select {
		width: 200px;
		padding-left: 12px;
	}

	.table-container {
		background: white;
		border-radius: 12px;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
		overflow: hidden;
	}

	table {
		width: 100%;
		border-collapse: collapse;
		text-align: left;
	}

	th {
		background-color: #f8fafc;
		padding: 16px 24px;
		font-size: 0.875rem;
		font-weight: 600;
		color: #64748b;
		border-bottom: 1px solid #e2e8f0;
	}

	td {
		padding: 12px 24px;
		border-bottom: 1px solid #f1f5f9;
		font-size: 0.875rem;
	}

	.ts {
		color: #64748b;
		font-family: monospace;
	}

	.topic-tag {
		background: #f1f5f9;
		color: #475569;
		padding: 4px 8px;
		border-radius: 4px;
		font-size: 0.75rem;
		font-weight: 600;
	}

	.device-id {
		color: #0ea5e9;
		font-weight: 600;
	}

	.fade-in {
		animation: fadeIn 0.5s ease-out;
	}

	@keyframes fadeIn {
		from { opacity: 0; transform: translateY(-10px); }
		to { opacity: 1; transform: translateY(0); }
	}

	.btn-icon {
		background: none;
		border: 1px solid #e2e8f0;
		color: #64748b;
		padding: 6px;
		border-radius: 6px;
		cursor: pointer;
	}

	/* Modal */
	.modal-overlay {
		position: fixed;
		top: 0; left: 0; right: 0; bottom: 0;
		background: rgba(15, 23, 42, 0.5);
		display: flex; align-items: center; justify-content: center;
		z-index: 1000;
	}

	.modal {
		background: white;
		width: 90%; max-width: 600px;
		max-height: 80vh;
		border-radius: 12px; padding: 24px;
		display: flex; flex-direction: column;
	}

	.modal-header {
		display: flex; justify-content: space-between; align-items: center;
		margin-bottom: 16px;
	}

	.json-viewer {
		background: #1e293b;
		color: #e2e8f0;
		padding: 16px;
		border-radius: 8px;
		overflow: auto;
		flex: 1;
		font-family: monospace;
		font-size: 0.875rem;
	}
</style>
