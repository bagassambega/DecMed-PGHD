import type { InvokeGetPghdListItem, InvokeGetPghdResponseData, SuccessResponse } from '$lib/types';
import { sanitizeInputText, tryCatchAsVal } from '$lib/utils';
import { invoke } from '@tauri-apps/api/core';
import { toast } from 'svelte-sonner';

type Props = {
	accessToken: string;
	patientIotaAddress: string;
};

export class PghdReadState {
	accessToken = $state('');
	patientIotaAddress = $state('');
	fetchPghdList = $state<Promise<InvokeGetPghdListItem[]>>(Promise.resolve([]));

	constructor({ accessToken, patientIotaAddress }: Props) {
		this.accessToken = accessToken;
		this.patientIotaAddress = patientIotaAddress;
		this.refreshPghdList();
	}

	getPghdList = async (accessToken: string, patientIotaAddress: string) => {
		const resInvokeGetPghdList = await tryCatchAsVal(async () => {
			return (await invoke('get_pghd_list', {
				accessToken,
				patientIotaAddress
			})) as SuccessResponse<InvokeGetPghdListItem[]>;
		});

		if (!resInvokeGetPghdList.success) {
			const errorMessage = explainPghdAccessError(resInvokeGetPghdList.error);
			toast.error(errorMessage);
			throw new Error(errorMessage);
		}

		return resInvokeGetPghdList.data.data;
	};

	getPghd = async (accessToken: string, index: number, patientIotaAddress: string) => {
		const resInvokeGetPghd = await tryCatchAsVal(async () => {
			return (await invoke('get_pghd', {
				accessToken,
				index,
				patientIotaAddress
			})) as SuccessResponse<InvokeGetPghdResponseData>;
		});

		if (!resInvokeGetPghd.success) {
			const errorMessage = explainPghdAccessError(resInvokeGetPghd.error);
			toast.error(errorMessage);
			throw new Error(errorMessage);
		}

		const pghd = resInvokeGetPghd.data.data;
		if (!pghd) {
			const message = 'PGHD response data is empty.';
			toast.error(message);
			throw new Error(message);
		}

		toast.success('PGHD batch opened and verified.');
		return pghd;
	};

	invalidatePghd = async (cid: string, failureReason: string) => {
		const resInvalidatePghd = await tryCatchAsVal(async () => {
			return (await invoke('invalidate_pghd', {
				accessToken: this.accessToken,
				cid: sanitizeInputText(cid, 256),
				failureReason: sanitizeInputText(failureReason, 500),
				patientIotaAddress: this.patientIotaAddress
			})) as SuccessResponse<null>;
		});

		if (!resInvalidatePghd.success) {
			toast.error(resInvalidatePghd.error);
			return;
		}

		toast.success('PGHD entry invalidated.');
		this.refreshPghdList();
	};

	refreshPghdList = () => {
		this.fetchPghdList = this.getPghdList(this.accessToken, this.patientIotaAddress);
	};
}

const explainPghdAccessError = (error: string) => {
	const lower = error.toLowerCase();
	if (
		error.includes('Keys not found') ||
		lower.includes('expired') ||
		lower.includes('invalid token') ||
		lower.includes('unauthorized')
	) {
		return withRawError(
			'PGHD PRE access keys are missing or expired. Re-grant PGHD access from the Android patient app using this personnel QR, then refresh this page.',
			error
		);
	}

	if (
		error.includes('ERR_DATA_CORRUPTED') ||
		error.includes('INNER_SIGNATURE_INVALID') ||
		lower.includes('signature error') ||
		lower.includes('invalid pghd') ||
		lower.includes('hash') ||
		lower.includes('h_cipher') ||
		lower.includes('corrupt')
	) {
		return withRawError(
			'Integrity warning: this PGHD batch failed hash or digital signature verification. Treat this data as invalid and do not use it for clinical decisions.',
			error
		);
	}

	return error;
};

const withRawError = (summary: string, error: string) => `${summary}\n\nTechnical detail:\n${error}`;
