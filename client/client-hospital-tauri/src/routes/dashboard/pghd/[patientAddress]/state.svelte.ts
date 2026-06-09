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

	constructor({ accessToken, patientIotaAddress }: Props) {
		this.accessToken = accessToken;
		this.patientIotaAddress = patientIotaAddress;
	}

	getPghdList = async (accessToken: string, patientIotaAddress: string) => {
		const resInvokeGetPghdList = await tryCatchAsVal(async () => {
			return (await invoke('get_pghd_list', {
				accessToken,
				patientIotaAddress
			})) as SuccessResponse<InvokeGetPghdListItem[]>;
		});

		if (!resInvokeGetPghdList.success) {
			toast.error(resInvokeGetPghdList.error);
			throw new Error(resInvokeGetPghdList.error);
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
			toast.error(resInvokeGetPghd.error);
			throw new Error(resInvokeGetPghd.error);
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
	};

	fetchPghdList = $derived(this.getPghdList(this.accessToken, this.patientIotaAddress));
}
