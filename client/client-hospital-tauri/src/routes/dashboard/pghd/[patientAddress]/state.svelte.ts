import type { InvokeGetPghdListItem, InvokeGetPghdResponseData, SuccessResponse } from '$lib/types';
import { tryCatchAsVal } from '$lib/utils';
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
				cid,
				failureReason,
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
	if (
		error.includes('Keys not found') ||
		error.toLowerCase().includes('expired') ||
		error.toLowerCase().includes('invalid token') ||
		error.toLowerCase().includes('unauthorized')
	) {
		return 'PGHD PRE access keys are missing or expired. Re-grant PGHD access from the Android patient app using this personnel QR, then refresh this page.';
	}

	return error;
};
