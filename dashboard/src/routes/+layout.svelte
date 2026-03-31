<script>
	import { Monitor, Activity, Settings, LayoutDashboard, Share2, SearchCode, LineChart } from 'lucide-svelte';
	import { page } from '$app/stores';

	const navItems = [
		{ name: 'Dashboard', href: '/', icon: LayoutDashboard },
		{ name: 'Devices', href: '/devices', icon: Monitor },
		{ name: 'Message Inspector', href: '/streams', icon: Activity },
		{ name: 'Pipeline Topology', href: '/topology', icon: Share2 },
		{ name: 'Trace Timeline', href: '/trace', icon: SearchCode },
		{ name: 'Analytics', href: 'http://localhost:8888?token=brain', icon: LineChart, target: '_blank' },
		{ name: 'Settings', href: '/settings', icon: Settings }
	];
</script>

<div class="app-container">
	<nav class="sidebar">
		<div class="logo">
			<h2>Brain Edge</h2>
		</div>
		<ul class="nav-links">
			{#each navItems as item}
				<li>
					<a href={item.href} class:active={$page.url.pathname === item.href} target={item.target || '_self'}>
						<svelte:component this={item.icon} size={20} />
						<span>{item.name}</span>
					</a>
				</li>
			{/each}
		</ul>
	</nav>

	<main class="content">
		<slot />
	</main>
</div>

<style>
	:global(body) {
		margin: 0;
		padding: 0;
		font-family:
			system-ui,
			-apple-system,
			sans-serif;
		background-color: #f8fafc;
	}

	.app-container {
		display: flex;
		height: 100vh;
	}

	.sidebar {
		width: 260px;
		background-color: #1e293b;
		color: white;
		display: flex;
		flex-direction: column;
		padding: 20px;
	}

	.logo h2 {
		margin: 0 0 40px 0;
		font-size: 1.5rem;
		color: #38bdf8;
	}

	.nav-links {
		list-style: none;
		padding: 0;
		margin: 0;
	}

	.nav-links li {
		margin-bottom: 8px;
	}

	.nav-links a {
		display: flex;
		align-items: center;
		gap: 12px;
		color: #94a3b8;
		text-decoration: none;
		padding: 12px;
		border-radius: 8px;
		transition: all 0.2s;
	}

	.nav-links a:hover {
		background-color: #334155;
		color: white;
	}

	.nav-links a.active {
		background-color: #0ea5e9;
		color: white;
	}

	.content {
		flex: 1;
		overflow-y: auto;
		padding: 40px;
	}
</style>
