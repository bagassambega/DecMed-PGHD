import { error } from '@sveltejs/kit';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ parent, params, url }) => {
	await parent();

	const patientIotaAddress = params.patientAddress;
	const accessToken = url.searchParams.get('accessToken');
	const patientName = url.searchParams.get('patientName') ?? patientIotaAddress;

	if (!accessToken) {
		return error(404);
	}

	return {
		accessToken,
		patientName,
		patientIotaAddress
	};
};
