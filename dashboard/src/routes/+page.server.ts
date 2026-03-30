import * as ch from '$lib/server/clickhouse';

export async function load() {
	try {
		const [rawCount, sessionCount, alertCount] = await Promise.all([
			ch.query('SELECT count() as count FROM brain.rawframes'),
			ch.query('SELECT count() as count FROM brain.session_features'),
			ch.query('SELECT count() as count FROM brain.inference_alerts')
		]);

		const throughput = await ch.query(`
			SELECT 
				toStartOfSecond(ts) as time,
				count() as count
			FROM brain.rawframes
			WHERE ts > now() - INTERVAL 1 MINUTE
			GROUP BY time
			ORDER BY time
		`);

		const activeDevices = await ch.query(`
			SELECT count(DISTINCT device_id) as count 
			FROM brain.rawframes 
			WHERE ts > now() - INTERVAL 1 HOUR
		`);

		return {
			stats: {
				totalEvents: rawCount.data[0]?.count || 0,
				totalSessions: sessionCount.data[0]?.count || 0,
				totalAlerts: alertCount.data[0]?.count || 0,
				activeDevices: activeDevices.data[0]?.count || 0
			},
			throughput: throughput.data.map((r: any) => ({
				time: r.time,
				count: parseInt(r.count)
			}))
		};
	} catch (e) {
		console.error('Failed to load dashboard data:', e);
		return {
			error: 'ClickHouse Connection Failed',
			stats: { totalEvents: 0, totalSessions: 0, totalAlerts: 0, activeDevices: 0 },
			throughput: []
		};
	}
}
