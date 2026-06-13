import { z } from 'zod';
import { ADMINISTRATIVE_PERSONNEL_ROLE, MEDICAL_PERSONNEL_ROLE } from './constants';

const sanitizedString = (maxLength: number, errors: { required: string; invalid: string }) =>
	z
		.string({
			required_error: errors.required,
			invalid_type_error: errors.invalid
		})
		.trim()
		.max(maxLength, { message: errors.invalid });

const pinSchema = {
	pin: sanitizedString(6, { required: 'PIN is required.', invalid: 'PIN is invalid.' })
		.trim()
		.regex(/^\d{6}$/, { message: 'PIN is invalid.' })
		.min(1, { message: 'PIN is required.' })
		.max(6, { message: 'PIN maximum 6 digits.' })
};

const nameSchema = {
	name: sanitizedString(100, { required: 'Name is required.', invalid: 'Name is invalid.' })
		.trim()
		.regex(/^[a-zA-Z0-9 ]{2,100}$/, {
			message: 'Name must consist of alphanumeric characters only of length 2 - 100.'
		})
};

export const medicalDataMainCategory = {
	mainCategory: z.enum(['Category1', 'Category2'])
};

export const medicalDataSubCategory = {
	subCategory: z.enum(['SubCategory1', 'SubCategory2'])
};

const anamnesisSchema = {
	anamnesis: sanitizedString(1000, {
		required: 'Anamnesis is required.',
		invalid: 'Anamnesis is invalid.'
	})
		.trim()
		.regex(/^[a-zA-Z0-9:,.\\ ]{2,1000}$/, {
			message: 'Anamnesis must consist of alphanumeric characters only of length 2 - 100.'
		})
};

const physicalCheckSchema = {
	physicalCheck: sanitizedString(1000, {
		required: 'Physical check is required.',
		invalid: 'Physical check is invalid.'
	})
		.trim()
		.regex(/^[a-zA-Z0-9:,.\\ ]{2,1000}$/, {
			message: 'Physical check must consist of alphanumeric characters only of length 2 - 100.'
		})
};

const psychologicalCheckSchema = {
	psychologicalCheck: sanitizedString(1000, {
		required: 'Psychological check is required.',
		invalid: 'Psychological check is invalid.'
	})
		.trim()
		.regex(/^[a-zA-Z0-9:,.\\ ]{2,1000}$/, {
			message: 'Psychological check must consist of alphanumeric characters only of length 2 - 100.'
		})
};

const diagnoseSchema = {
	diagnose: sanitizedString(1000, {
		required: 'Diagnose is required.',
		invalid: 'Diagnose is invalid.'
	})
		.trim()
		.regex(/^[a-zA-Z0-9:,.\\ ]{2,1000}$/, {
			message: 'Diagnose must consist of alphanumeric characters only of length 2 - 100.'
		})
};

const therapySchema = {
	therapy: sanitizedString(1000, {
		required: 'Therapy is required.',
		invalid: 'Therapy is invalid.'
	})
		.trim()
		.regex(/^[a-zA-Z0-9:,.\\ ]{2,1000}$/, {
			message: 'Therapy must consist of alphanumeric characters only of length 2 - 100.'
		})
};

// const _hospitalSchema = {
// 	hospital: z
// 		.string({ required_error: 'Hospital is required.', invalid_type_error: 'Hospital is invalid.' })
// 		.trim()
// 		.regex(/^[a-zA-Z0-9 ]{2,100}$/, {
// 			message: 'Hospital must consist of alphanumeric characters only of length 2 - 100.'
// 		})
// 		.transform((val) => val.trim())
// };

export const activationSchema = z.object({
	id: sanitizedString(128, { required: 'ID is required.', invalid: 'ID is invalid.' })
		.trim()
		.min(1, { message: 'ID is required.' }),
	activationKey: sanitizedString(36, {
		required: 'Activation Key is required.',
		invalid: 'Activation Key is invalid.'
	})
		.trim()
		.min(1, { message: 'Activation Key is required.' })
		.max(36, { message: 'Activation Key is invalid.' })
});

export const signInSchemaStep1 = z.object(pinSchema);

export const signInSchemaStep2 = signInSchemaStep1.extend({
	confirmPin: sanitizedString(6, {
		required: 'Confirm PIN is required.',
		invalid: 'Confirm PIN is invalid.'
	})
		.trim()
		.regex(/^\d{6}$/, { message: 'Confirm PIN is invalid.' })
		.min(1, { message: 'Confirm PIN is required.' })
		.max(6, { message: 'Confirm PIN maximum 6 digits.' })
});

export const signInSchemaStep3 = signInSchemaStep2
	.extend({
		seedWords: z
			.string({
				required_error: 'Seed Words is required.',
				invalid_type_error: 'Seed Words is invalid.'
			})
			.trim()
			.max(256, { message: 'Seed Words is invalid.' })
			.min(1, { message: 'Seed Words is required.' })
			.refine(
				(val: string) => {
					const words = val.split(' ');
					return words.length === 12;
				},
				{
					message: 'Seed Words is invalid.'
				}
			)
	})
	.superRefine((val, ctx) => {
		if (val.pin !== val.confirmPin) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				path: ['confirmPin'],
				message: 'PIN and Confirm PIN must be same.'
			});
		}
	});

export const signUpSchemaStep1 = signInSchemaStep1;
export const signUpSchemaStep2 = signInSchemaStep2;
export const signUpSchemaStep4 = signInSchemaStep3;

export const addPersonnelSchemaStep1 = z.object({
	id: sanitizedString(128, { required: 'ID is required.', invalid: 'ID is invalid.' })
		.trim()
		.min(1, { message: 'ID is required.' }),
	role: z.enum([ADMINISTRATIVE_PERSONNEL_ROLE, MEDICAL_PERSONNEL_ROLE], {
		required_error: 'Role is required.',
		invalid_type_error: 'Role is invalid.'
	})
});

export const addPersonnelSchemaStep2 = addPersonnelSchemaStep1.extend(pinSchema);
export const completeProfileAdminSchema = z.object(nameSchema);
export const completeProfilePersonnelSchema = z.object(nameSchema);
export const createMedicalRecordSchema = z
	.object(anamnesisSchema)
	.extend(physicalCheckSchema)
	.extend(psychologicalCheckSchema)
	.extend(diagnoseSchema)
	.extend(therapySchema);
export const updateMedicalRecordSchema = z
	.object(anamnesisSchema)
	.extend(physicalCheckSchema)
	.extend(psychologicalCheckSchema)
	.extend(diagnoseSchema)
	.extend(therapySchema);

export const addPersonnelSchemas = [addPersonnelSchemaStep1, addPersonnelSchemaStep2];
export const signInSchemas = [signInSchemaStep1, signInSchemaStep2, signInSchemaStep3];
export const signUpSchemas = [
	signUpSchemaStep1,
	signUpSchemaStep2,
	signUpSchemaStep2,
	signUpSchemaStep4
];
