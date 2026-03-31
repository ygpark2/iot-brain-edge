<script lang="ts">
	import { onDestroy, onMount } from 'svelte';
	import { Search, Eye, RefreshCcw } from 'lucide-svelte';

	import {
		HISTORY_TOPICS,
		LIVE_TOPICS,
		type HistoryTopic,
		type InspectorMessage,
		type LiveTopic
	} from '../../lib/inspector';

	let messages: InspectorMessage[] = [];
	let selectedMessage: InspectorMessage | null = null;
	let isStreaming = true;
	let currentTab: 'live' | 'history' = 'live';
	let selectedLiveTopic: LiveTopic = 'all';
	let selectedHistoryTopic: HistoryTopic = 'all';
	let searchQuery = '';
	let error = '';
	let liveStatus = 'idle';
	let liveError = '';
	let pollHandle: ReturnType<typeof setInterval> | undefined;

	$: emptyStateMessage =
		currentTab === 'history'
			? searchQuery.trim()
				? `No ClickHouse history matches search="${searchQuery.trim()}" for topic "${selectedHistoryTopic}".`
				: `No ClickHouse history rows for topic "${selectedHistoryTopic}" yet.`
			: searchQuery.trim()
				? `No Kafka live messages match search="${searchQuery.trim()}" for topic "${selectedLiveTopic}".`
				: `No Kafka live messages for topic "${selectedLiveTopic}" yet.`;

	async function refreshCurrentTab() {
		if (currentTab === 'live') {
			await loadLive();
			return;
		}

		await loadHistory();
	}

	async function loadLive() {
		try {
			const params = new URLSearchParams({
				topic: selectedLiveTopic,
				search: searchQuery,
				limit: '20'
			});
			const res = await fetch(`/api/streams/live?${params.toString()}`);
			const json = await res.json();
			messages = json.messages || [];
			liveStatus = json.status || 'error';
			liveError = json.error || '';
			error = '';
		} catch (e) {
			error = String(e);
		}
	}

	async function loadHistory() {
		try {
			const params = new URLSearchParams({
				topic: selectedHistoryTopic,
				search: searchQuery,
				limit: '50'
			});
			const res = await fetch(`/api/streams/history?${params.toString()}`);
			const json = await res.json();
			if (!res.ok) throw new Error(json.error || 'Failed to load history');
			messages = json.messages || [];
			error = '';
		} catch (e) {
			error = String(e);
		}
	}

	onMount(() => {
		void refreshCurrentTab();
		pollHandle = setInterval(() => {
			if (currentTab === 'live' && isStreaming) {
				void loadLive();
			}
		}, 2000);
	});

	onDestroy(() => {
		if (pollHandle) clearInterval(pollHandle);
	});
</script>

<div class="streams-page">
	<header>
		<div class="title-section">
			<h1>Message Inspector</h1>
			<p>Live data from Kafka, searchable history from ClickHouse</p>
		</div>
		<div class="controls">
			<button class:active={currentTab === 'live'} class="btn-secondary" on:click={() => { currentTab = 'live'; void refreshCurrentTab(); }}>
				Live
			</button>
			<button class:active={currentTab === 'history'} class="btn-secondary" on:click={() => { currentTab = 'history'; void refreshCurrentTab(); }}>
				History
			</button>
			<button class="btn-secondary" on:click={() => (isStreaming = !isStreaming)}>
				<span style:animation={isStreaming ? 'spin 2s linear infinite' : 'none'}>
					<RefreshCcw size={18} />
				</span>
				{isStreaming ? 'Pause Auto Refresh' : 'Resume Auto Refresh'}
			</button>
			<button class="btn-secondary" on:click={() => void refreshCurrentTab()}>
				<RefreshCcw size={18} />
				Refresh
			</button>
		</div>
	</header>

	<div class="status-row">
		<span class="mode-chip">{currentTab === 'live' ? 'Kafka Live' : 'ClickHouse History'}</span>
		{#if currentTab === 'live'}
			<span class="mode-chip {liveStatus === 'connected' ? 'ok' : liveStatus === 'error' ? 'bad' : 'pending'}">
				{liveStatus}
			</span>
		{/if}
	</div>

	<div class="filters">
		<div class="search-wrapper">
			<span class="search-icon"><Search size={18} /></span>
			<input
				type="text"
				placeholder="Filter by Device ID, Topic, Event Type..."
				bind:value={searchQuery}
				on:change={() => void refreshCurrentTab()}
			/>
		</div>
		{#if currentTab === 'live'}
			<select bind:value={selectedLiveTopic} on:change={() => void refreshCurrentTab()}>
				{#each LIVE_TOPICS as topic}
					<option value={topic}>{topic}</option>
				{/each}
			</select>
		{:else}
			<select bind:value={selectedHistoryTopic} on:change={() => void refreshCurrentTab()}>
				{#each HISTORY_TOPICS as topic}
					<option value={topic}>{topic}</option>
				{/each}
			</select>
		{/if}
	</div>

	{#if error || liveError}
		<p class="error-banner">{error || liveError}</p>
	{/if}

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
						<td class="ts">{new Date(msg.timestamp).toLocaleTimeString()}</td>
						<td><span class="topic-tag">{msg.topic}</span></td>
						<td>{msg.eventType}</td>
						<td><span class="device-id">{msg.deviceId || '-'}</span></td>
						<td>{msg.size}</td>
						<td>
							<button class="btn-icon" on:click={() => (selectedMessage = msg)}>
								<Eye size={16} />
							</button>
						</td>
					</tr>
				{/each}
				{#if messages.length === 0}
					<tr>
						<td colspan="6" class="empty-state">{emptyStateMessage}</td>
					</tr>
				{/if}
			</tbody>
		</table>
	</div>

	{#if selectedMessage}
		<div class="modal-overlay">
			<button class="modal-backdrop" type="button" aria-label="Close message details" on:click={() => (selectedMessage = null)}></button>
			<div class="modal" role="dialog" aria-modal="true">
				<div class="modal-header">
					<h3>Message Detail: {selectedMessage.id}</h3>
					<button class="btn-close" on:click={() => (selectedMessage = null)}>×</button>
				</div>
				<div class="json-viewer">
					<pre>{JSON.stringify(selectedMessage.payload, null, 2)}</pre>
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

	.controls {
		display: flex;
		gap: 8px;
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

	.btn-secondary.active {
		border-color: #0ea5e9;
		color: #0369a1;
		background: #e0f2fe;
	}

	@keyframes spin {
		from { transform: rotate(0deg); }
		to { transform: rotate(360deg); }
	}

	.status-row {
		display: flex;
		gap: 8px;
		margin-bottom: 16px;
	}

	.mode-chip {
		display: inline-flex;
		align-items: center;
		padding: 6px 10px;
		border-radius: 9999px;
		background: #e2e8f0;
		color: #334155;
		font-size: 0.75rem;
		font-weight: 700;
	}

	.mode-chip.ok {
		background: #dcfce7;
		color: #166534;
	}

	.mode-chip.pending {
		background: #fef3c7;
		color: #92400e;
	}

	.mode-chip.bad {
		background: #fee2e2;
		color: #991b1b;
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
		width: 220px;
		padding-left: 12px;
	}

	.table-container {
		background: white;
		border-radius: 12px;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
		overflow: hidden;
	}

	.empty-state {
		padding: 36px 20px;
		text-align: center;
		color: #64748b;
		font-weight: 500;
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

	.modal-overlay {
		position: fixed;
		top: 0;
		left: 0;
		right: 0;
		bottom: 0;
		background: rgba(15, 23, 42, 0.5);
		display: flex;
		align-items: center;
		justify-content: center;
		z-index: 1000;
	}

	.modal-backdrop {
		position: absolute;
		inset: 0;
		border: 0;
		background: transparent;
		cursor: pointer;
	}

	.modal {
		background: white;
		width: 90%;
		max-width: 700px;
		max-height: 80vh;
		border-radius: 12px;
		padding: 24px;
		display: flex;
		flex-direction: column;
	}

	.modal-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
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

	.error-banner {
		color: #b91c1c;
		font-size: 0.875rem;
		margin: 0 0 16px 0;
	}
</style>
