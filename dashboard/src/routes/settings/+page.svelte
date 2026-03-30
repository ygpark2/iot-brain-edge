<div class="settings-page">
	<header>
		<h1>Settings</h1>
		<p>Global system configuration and dashboard preferences</p>
	</header>

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
</style>
