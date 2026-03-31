<script lang="ts">
	import { onMount } from 'svelte';

	type SettingsData = {
		currentTtlDays: number;
		s3: {
			endpoint: string;
			region: string;
			access_key: string;
			secret_key: string;
		};
	};

	let data: SettingsData = {
		currentTtlDays: 30,
		s3: {
			endpoint: 'http://minio:9000/brain/clickhouse/',
			region: '',
			access_key: 'minio',
			secret_key: 'minio12345'
		}
	};
	let ttlMessage = '';
	let ttlSuccess = false;
	let s3Message = '';
	let s3Success = false;
	let error = '';

	async function loadSettings() {
		try {
			const res = await fetch('/api/settings');
			const json = await res.json();
			if (!res.ok) throw new Error(json.error || 'Failed to load settings');
			data = {
				currentTtlDays: json.currentTtlDays ?? 30,
				s3: {
					endpoint: json.s3?.endpoint || 'http://minio:9000/brain/clickhouse/',
					region: json.s3?.region || '',
					access_key: json.s3?.access_key || 'minio',
					secret_key: json.s3?.secret_key || 'minio12345'
				}
			};
			error = '';
		} catch (e) {
			error = String(e);
		}
	}

	onMount(() => {
		void loadSettings();
	});

	async function submitS3() {
		const res = await fetch('/api/settings/s3', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({
				endpoint: data.s3.endpoint,
				region: data.s3.region,
				accessKey: data.s3.access_key,
				secretKey: data.s3.secret_key
			})
		});
		const json = await res.json();
		s3Success = !!json.success;
		s3Message = json.message || json.error || '';
	}

	async function submitTtl() {
		const res = await fetch('/api/settings/ttl', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ ttlDays: data.currentTtlDays })
		});
		const json = await res.json();
		ttlSuccess = !!json.success;
		ttlMessage = json.message || json.error || '';
	}
</script>

<div class="settings-page">
	<header>
		<h1>Settings</h1>
		<p>Global system configuration and dashboard preferences</p>
	</header>

	{#if error}
		<p class="error-banner">{error}</p>
	{/if}

	<div class="settings-grid">
		<section class="settings-card">
			<h3>Connection Settings</h3>
			<div class="form-group">
				<label for="ch-host">ClickHouse Host</label>
				<input type="text" id="ch-host" value="http://localhost:8123" />
			</div>
			<div class="form-group">
				<label for="kafka-host">Kafka Bootstrap Servers</label>
				<input type="text" id="kafka-host" value="localhost:9094" />
			</div>
			<button class="btn-primary">Save Connections</button>
		</section>

		<section class="settings-card">
			<h3>AWS S3 / Cold Storage Configuration</h3>
			<p style="font-size: 0.875rem; color: #64748b; margin-bottom: 20px;">
				Configure AWS S3 or compatible (MinIO) storage for data archiving.
			</p>
			
			<form on:submit|preventDefault={submitS3}>
				<div class="form-group">
					<label for="s3-endpoint">S3 Endpoint</label>
					<input type="text" id="s3-endpoint" name="endpoint" bind:value={data.s3.endpoint} placeholder="https://s3.amazonaws.com/your-bucket/" />
				</div>
				<div class="form-group">
					<label for="s3-region">Region</label>
					<input type="text" id="s3-region" name="region" bind:value={data.s3.region} placeholder="us-east-1" />
				</div>
				<div class="form-group">
					<label for="s3-access-key">Access Key ID</label>
					<input type="text" id="s3-access-key" name="access_key" bind:value={data.s3.access_key} />
				</div>
				<div class="form-group">
					<label for="s3-secret-key">Secret Access Key</label>
					<input type="password" id="s3-secret-key" name="secret_key" bind:value={data.s3.secret_key} />
				</div>
				<button type="submit" class="btn-primary">Update S3 Settings</button>
			</form>

			{#if s3Message}
				<p style="margin-top: 12px; color: {s3Success ? '#10b981' : '#ef4444'}; font-size: 0.875rem;">{s3Message}</p>
			{/if}
		</section>

		<section class="settings-card">
			<h3>Data Retention (Tiered Storage)</h3>
			<p style="font-size: 0.875rem; color: #64748b; margin-bottom: 20px;">
				Configure how long data is kept in hot storage before moving to S3 (Cold Storage).
			</p>
			
			<form on:submit|preventDefault={submitTtl}>
				<div class="form-group">
					<label for="ttl-days">Move to S3 after (Days)</label>
					<div style="display: flex; align-items: center; gap: 12px;">
						<input type="number" id="ttl-days" name="ttl_days" bind:value={data.currentTtlDays} min="1" max="3650" />
						<span style="font-size: 0.875rem; color: #64748b;">Days</span>
					</div>
				</div>
				<button type="submit" class="btn-primary">Update Policy</button>
			</form>

			{#if ttlMessage}
				<p style="margin-top: 12px; color: {ttlSuccess ? '#10b981' : '#ef4444'}; font-size: 0.875rem;">{ttlMessage}</p>
			{/if}
		</section>

		<section class="settings-card">
			<h3>Admin User</h3>
			<div class="user-info">
				<div class="avatar">A</div>
				<div>
					<strong>admin@ainsoft.com</strong>
					<p>Super Administrator</p>
				</div>
			</div>
			<button class="btn-secondary">Change Password</button>
		</section>
	</div>
</div>

<style>
	header { margin-bottom: 32px; }
	header h1 { margin: 0; font-size: 1.875rem; color: #0f172a; }

	.settings-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
		gap: 24px;
	}

	.settings-card {
		background: white;
		padding: 24px;
		border-radius: 12px;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
	}

	.settings-card h3 { margin-top: 0; margin-bottom: 20px; font-size: 1.125rem; }

	.form-group { margin-bottom: 16px; }
	.form-group label { display: block; font-size: 0.875rem; color: #64748b; margin-bottom: 6px; }
	.form-group input { width: 100%; padding: 10px; border: 1px solid #e2e8f0; border-radius: 8px; box-sizing: border-box; }

	.btn-primary { background: #0ea5e9; color: white; border: none; padding: 10px 20px; border-radius: 8px; font-weight: 600; cursor: pointer; }
	.btn-secondary { background: white; border: 1px solid #e2e8f0; padding: 10px 20px; border-radius: 8px; font-weight: 600; cursor: pointer; margin-top: 12px; }

	.user-info { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; }
	.avatar { width: 48px; height: 48px; background: #0ea5e9; color: white; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 1.25rem; font-weight: 700; }
	.error-banner { color: #b91c1c; font-size: 0.875rem; margin: 0 0 24px 0; }
</style>
