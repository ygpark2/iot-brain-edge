<script>
	import { Plus, Search, Edit2, Trash2, X, Check } from 'lucide-svelte';

	let devices = [
		{ id: 'device-001', location: 'Factory Floor A', sensors: 'GRF, Plantar', status: 'Online' },
		{ id: 'device-002', location: 'Warehouse B', sensors: '3D Scanner', status: 'Online' },
		{ id: 'device-003', location: 'Lab 1', sensors: 'GRF', status: 'Offline' }
	];

	let isAdding = false;
	let editingId = null;
	let searchQuery = '';

	let newDevice = { id: '', location: '', sensors: '', status: 'Online' };

	function addDevice() {
		devices = [...devices, { ...newDevice }];
		resetForm();
	}

	function deleteDevice(id) {
		devices = devices.filter((d) => d.id !== id);
	}

	function startEdit(device) {
		editingId = device.id;
		newDevice = { ...device };
		isAdding = true;
	}

	function saveEdit() {
		devices = devices.map((d) => (d.id === editingId ? { ...newDevice } : d));
		resetForm();
	}

	function resetForm() {
		newDevice = { id: '', location: '', sensors: '', status: 'Online' };
		isAdding = false;
		editingId = null;
	}

	$: filteredDevices = devices.filter(
		(d) =>
			d.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
			d.location.toLowerCase().includes(searchQuery.toLowerCase())
	);
</script>

<div class="devices-page">
	<header>
		<div class="title-section">
			<h1>Device Management</h1>
			<p>Register and manage your edge devices</p>
		</div>
		<button class="btn-primary" on:click={() => (isAdding = true)}>
			<Plus size={20} /> Add Device
		</button>
	</header>

	<div class="table-controls">
		<div class="search-wrapper">
			<Search size={18} class="search-icon" />
			<input type="text" placeholder="Search devices..." bind:value={searchQuery} />
		</div>
	</div>

	<div class="table-container">
		<table>
			<thead>
				<tr>
					<th>Device ID</th>
					<th>Location</th>
					<th>Sensor Types</th>
					<th>Status</th>
					<th>Actions</th>
				</tr>
			</thead>
			<tbody>
				{#each filteredDevices as device}
					<tr>
						<td><span class="device-id">{device.id}</span></td>
						<td>{device.location}</td>
						<td>{device.sensors}</td>
						<td>
							<span class="status-dot {device.status.toLowerCase()}"></span>
							{device.status}
						</td>
						<td>
							<div class="actions">
								<button class="btn-icon" on:click={() => startEdit(device)} title="Edit">
									<Edit2 size={16} />
								</button>
								<button class="btn-icon delete" on:click={() => deleteDevice(device.id)} title="Delete">
									<Trash2 size={16} />
								</button>
							</div>
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>

	{#if isAdding}
		<div class="modal-overlay">
			<div class="modal">
				<div class="modal-header">
					<h3>{editingId ? 'Edit Device' : 'Register New Device'}</h3>
					<button class="btn-close" on:click={resetForm}><X size={20} /></button>
				</div>
				<form on:submit|preventDefault={editingId ? saveEdit : addDevice}>
					<div class="form-group">
						<label for="deviceId">Device ID</label>
						<input
							id="deviceId"
							type="text"
							bind:value={newDevice.id}
							disabled={!!editingId}
							required
						/>
					</div>
					<div class="form-group">
						<label for="location">Location</label>
						<input id="location" type="text" bind:value={newDevice.location} required />
					</div>
					<div class="form-group">
						<label for="sensors">Sensor Types (comma separated)</label>
						<input id="sensors" type="text" bind:value={newDevice.sensors} required />
					</div>
					<div class="form-group">
						<label for="status">Initial Status</label>
						<select id="status" bind:value={newDevice.status}>
							<option value="Online">Online</option>
							<option value="Offline">Offline</option>
						</select>
					</div>
					<div class="modal-footer">
						<button type="button" class="btn-secondary" on:click={resetForm}>Cancel</button>
						<button type="submit" class="btn-primary">
							<Check size={18} /> {editingId ? 'Save Changes' : 'Add Device'}
						</button>
					</div>
				</form>
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

	header p {
		margin: 4px 0 0 0;
		color: #64748b;
	}

	.btn-primary {
		background-color: #0ea5e9;
		color: white;
		border: none;
		padding: 10px 20px;
		border-radius: 8px;
		font-weight: 600;
		display: flex;
		align-items: center;
		gap: 8px;
		cursor: pointer;
		transition: background-color 0.2s;
	}

	.btn-primary:hover {
		background-color: #0284c7;
	}

	.table-controls {
		margin-bottom: 24px;
	}

	.search-wrapper {
		position: relative;
		width: 300px;
	}

	.search-icon {
		position: absolute;
		left: 12px;
		top: 50%;
		transform: translateY(-50%);
		color: #94a3b8;
	}

	.search-wrapper input {
		width: 100%;
		padding: 10px 12px 10px 40px;
		border: 1px solid #e2e8f0;
		border-radius: 8px;
		outline: none;
	}

	.search-wrapper input:focus {
		border-color: #0ea5e9;
		box-shadow: 0 0 0 2px rgba(14, 165, 233, 0.1);
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
		padding: 16px 24px;
		border-bottom: 1px solid #f1f5f9;
		color: #334155;
	}

	.device-id {
		font-family: monospace;
		font-weight: 600;
		color: #0ea5e9;
	}

	.status-dot {
		display: inline-block;
		width: 8px;
		height: 8px;
		border-radius: 50%;
		margin-right: 8px;
	}

	.status-dot.online {
		background-color: #10b981;
	}

	.status-dot.offline {
		background-color: #94a3b8;
	}

	.actions {
		display: flex;
		gap: 8px;
	}

	.btn-icon {
		background: none;
		border: 1px solid #e2e8f0;
		color: #64748b;
		padding: 6px;
		border-radius: 6px;
		cursor: pointer;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.btn-icon:hover {
		background-color: #f8fafc;
		color: #0ea5e9;
		border-color: #0ea5e9;
	}

	.btn-icon.delete:hover {
		color: #ef4444;
		border-color: #ef4444;
	}

	/* Modal */
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

	.modal {
		background: white;
		width: 100%;
		max-width: 500px;
		border-radius: 12px;
		padding: 24px;
		box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
	}

	.modal-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 24px;
	}

	.modal-header h3 {
		margin: 0;
		font-size: 1.25rem;
	}

	.btn-close {
		background: none;
		border: none;
		color: #94a3b8;
		cursor: pointer;
	}

	.form-group {
		margin-bottom: 20px;
	}

	.form-group label {
		display: block;
		margin-bottom: 6px;
		font-size: 0.875rem;
		font-weight: 500;
		color: #475569;
	}

	.form-group input,
	.form-group select {
		width: 100%;
		padding: 10px;
		border: 1px solid #e2e8f0;
		border-radius: 8px;
		box-sizing: border-box;
	}

	.modal-footer {
		display: flex;
		justify-content: flex-end;
		gap: 12px;
		margin-top: 32px;
	}

	.btn-secondary {
		background: white;
		border: 1px solid #e2e8f0;
		padding: 10px 20px;
		border-radius: 8px;
		cursor: pointer;
	}
</style>
