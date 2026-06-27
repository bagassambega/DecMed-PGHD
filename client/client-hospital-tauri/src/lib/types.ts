import type {
	addPersonnelSchemaStep2,
	completeProfileAdminSchema,
	completeProfilePersonnelSchema,
	createMedicalRecordSchema,
	medicalDataMainCategory,
	medicalDataSubCategory,
	signInSchemaStep3,
	signUpSchemaStep4,
	updateMedicalRecordSchema
} from './schema';
import { ADMIN_ROLE, ADMINISTRATIVE_PERSONNEL_ROLE, MEDICAL_PERSONNEL_ROLE } from './constants';
import type { z } from 'zod';

export type Role =
	| typeof ADMIN_ROLE
	| typeof MEDICAL_PERSONNEL_ROLE
	| typeof ADMINISTRATIVE_PERSONNEL_ROLE;

export type NavLink = {
	label: string;
	link: string;
	pageTitle: string;
};

export type Account = {
	role: Role;
	name: string;
	id: string;
};

export type HospitalPersonnel = {
	id: string;
	activation_key: string;
	role: Role;
};

export type SuccessResponse<T> = {
	status: string;
	data: T;
};

export type TauriAccessData = {
	accessDataTypes: TauriAccessDataType[];
	accessToken: string;
	exp: number;
	medicalMetadataIndex: number | null;
	patientIotaAddress: string;
	patientName: string;
	patientPrePublicKey: string | null;
};

export type TauriAccessDataType = 'Administrative' | 'Medical' | 'Pghd';

export type TauriMedicalData = {
	anamnesis: string;
	physical_check: string;
	psychological_check: string;
	diagnose: string;
	therapy: string;
};

export type TauriPatientPrivateAdministrativeData = {
	id: string;
	name: string | null;
	birth_place: string | null;
	date_of_birth: string | null;
	gender: string | null;
	religion: string | null;
	education: string | null;
	occupation: string | null;
	marital_status: string | null;
};

export type AdministrativeData = {
	id: string;
	idHash: string;
	name?: string;
	hospital?: string;
};

export type GetProfileData = {
	hospital: string | null;
	id: string;
	idHash: string;
	iotaAddress: string;
	iotaKeyPair: string;
	name: string | null;
	prePublicKey: string;
	role: Role;
};

export type InvokeGetMedicalRecordResponseData = {
	administrativeData: TauriPatientPrivateAdministrativeData;
	createdAt: string;
	medicalData: TauriMedicalData;
	currentIndex: number;
	nextIndex?: number | null;
	prevIndex?: number | null;
};

export type PghdMetadata = {
	batch_id?: string;
	cid?: string;
	created_at?: string;
	h_cipher?: string;
	patient_iota_address?: string;
	signature?: string;
	[key: string]: unknown;
};

export type PghdDataPoint = {
	timestamp: number;
	value: number | string | Record<string, unknown>;
	unit: string;
};

export type PghdDataGroup = {
	measurement_type: string;
	device_type: string;
	recording_method?: string;
	source: string;
	source_label?: string;
	source_package_name?: string;
	device_source?: string;
	data_points: PghdDataPoint[];
};

export type PghdBatchData = {
	schema_version: string;
	batch_id: string;
	patient_id: string;
	source_device: Record<string, unknown>;
	batch_period: {
		start_timestamp: number;
		end_timestamp: number;
	};
	collection_period?: {
		started_at: number;
		ended_at: number;
	};
	trigger_reason?: string;
	data_group: PghdDataGroup[];
};

export type InvokeGetPghdListItem = {
	cid: string;
	failure_reason: string;
	h_cipher: string;
	index: number;
	metadata: PghdMetadata;
	status: number;
	timestamp: number;
};

export type InvokeGetPghdResponseData = {
	cid: string;
	current_index: number;
	metadata: PghdMetadata;
	next_index?: number | null;
	payload_cid?: string | null;
	pghd_data: PghdBatchData;
	prev_index?: number | null;
	verified: boolean;
};

export type InvokeGetPatientAdministrativeDataResponseData = {
	administrativeData: TauriPatientPrivateAdministrativeData;
};

export type InvokeGlobalAdminAddActivationKeyData = {
	activationKey: string;
	id: string;
};

export type InvokeHospitalAdminAddActivationKeyResponse = {
	activationKey: string;
	id: string;
};

export type TryCatchAsValSuccess<T> = { success: true; data: T };
export type TryCatchAsValError = { success: false; error: string };
export type TryCatchAsValReturn<T> = TryCatchAsValSuccess<T> | TryCatchAsValError;

export type AddPersonnelSchemaStep2 = typeof addPersonnelSchemaStep2;
export type CompleteProfileAdminSchema = typeof completeProfileAdminSchema;
export type CompleteProfilePersonnelSchema = typeof completeProfilePersonnelSchema;
export type CreateMedicalRecordSchema = typeof createMedicalRecordSchema;
export type SignUpSchemaStep4 = typeof signUpSchemaStep4;
export type SignInSchemaStep3 = typeof signInSchemaStep3;
export type UpdateMedicalRecordSchema = typeof updateMedicalRecordSchema;

export type MedicalData = z.infer<typeof createMedicalRecordSchema>;
export type MedicalDataMainCategory = z.infer<typeof medicalDataMainCategory.mainCategory>;
export type MedicalDataSubCategory = z.infer<typeof medicalDataSubCategory.subCategory>;
