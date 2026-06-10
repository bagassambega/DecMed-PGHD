import type {
	InvokeGetMedicalRecordResponseData,
	InvokeGetPghdListItem,
	InvokeGetPghdResponseData,
	SuccessResponse
} from '$lib/types';
import { tryCatchAsVal } from '$lib/utils';
import { invoke } from '@tauri-apps/api/core';
import { toast } from 'svelte-sonner';

type Props = {
	accessToken: string | null;
	pghdAccessToken: string | null;
	index: number;
	patientIotaAddress: string;
};

export class EmrReadState {
	accessToken = $state<string | null>(null);
	pghdAccessToken = $state<string | null>(null);
	index = $state<number>(0);
	patientIotaAddress = $state('');
	fetchMedicalRecord = $state<Promise<InvokeGetMedicalRecordResponseData>>(new Promise(() => {}));
	fetchPghdList = $state<Promise<InvokeGetPghdListItem[]>>(Promise.resolve([]));

	constructor({ accessToken, pghdAccessToken, index, patientIotaAddress }: Props) {
		this.accessToken = accessToken;
		this.pghdAccessToken = pghdAccessToken;
		this.index = index;
		this.patientIotaAddress = patientIotaAddress;
		this.refreshMedicalRecord();
		this.refreshPghdList();
	}

	getMedicalRecord = async (
		accessToken: string | null,
		index: number | null,
		patientIotaAddress: string
	) => {
		const resInvokeGetMedicalRecord = await tryCatchAsVal(async () => {
			return (await invoke('get_medical_record', {
				accessToken,
				index,
				patientIotaAddress
			})) as SuccessResponse<InvokeGetMedicalRecordResponseData>;
		});

		console.log(resInvokeGetMedicalRecord);

		if (!resInvokeGetMedicalRecord.success) {
			toast.error(resInvokeGetMedicalRecord.error);
			throw new Error(resInvokeGetMedicalRecord.error);
		}

		return resInvokeGetMedicalRecord.data.data;
	};

	refreshMedicalRecord = () => {
		this.fetchMedicalRecord = this.getMedicalRecord(
			this.accessToken,
			this.index,
			this.patientIotaAddress
		);
	};

	getPghdList = async (accessToken: string | null, patientIotaAddress: string) => {
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

	getPghd = async (accessToken: string | null, index: number, patientIotaAddress: string) => {
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

		return pghd;
	};

	invalidatePghd = async (cid: string, failureReason: string) => {
		const resInvalidatePghd = await tryCatchAsVal(async () => {
			return (await invoke('invalidate_pghd', {
				accessToken: this.pghdAccessToken,
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

	refreshPghdList = () => {
		this.fetchPghdList = this.getPghdList(this.pghdAccessToken, this.patientIotaAddress);
	};
}

const explainPghdAccessError = (error: string) => {
	if (error.includes('Keys not found')) {
		return 'PGHD PRE access keys are missing or expired. Re-grant PGHD access from the Android patient app using this personnel QR, then refresh this page.';
	}

	return error;
};
