import { json } from '@sveltejs/kit';

import { getHistoryMessages } from '$lib/server/dashboard-api';

export async function GET({ url }) {
	try {
		return json({
			messages: await getHistoryMessages({
				topic: url.searchParams.get('topic'),
				search: url.searchParams.get('search'),
				limit: url.searchParams.get('limit')
			})
		});
	} catch (error) {
		return json({ messages: [], error: String(error) }, { status: 500 });
	}
}
