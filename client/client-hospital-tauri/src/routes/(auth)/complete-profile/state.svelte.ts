import { completeProfileAdminSchema, completeProfilePersonnelSchema } from '$lib/schema';
import type {
	CompleteProfileAdminSchema,
	CompleteProfilePersonnelSchema,
	Role,
	SuccessResponse
} from '$lib/types';
import { sanitizeInputText, tryCatchAsVal } from '$lib/utils';
import { invoke } from '@tauri-apps/api/core';
import { toast } from 'svelte-sonner';
import { superForm, type Infer, type SuperForm, type SuperValidated } from 'sveltekit-superforms';
import { zodClient } from 'sveltekit-superforms/adapters';

type Constructor = {
	role?: Role;
	completeProfileForm:
		| SuperValidated<Infer<CompleteProfileAdminSchema>>
		| SuperValidated<Infer<CompleteProfilePersonnelSchema>>;
};

export class CompleteProfileState {
	completeProfileMeta:
		| SuperForm<Infer<CompleteProfileAdminSchema>>
		| SuperForm<Infer<CompleteProfilePersonnelSchema>>;
	role?: Role;

	constructor({ completeProfileForm, role }: Constructor) {
		this.role = role;
		this.completeProfileMeta = superForm(completeProfileForm, {
			validators: zodClient(
				role === 'Admin' ? completeProfileAdminSchema : completeProfilePersonnelSchema
			),
			dataType: 'json',
			SPA: true,
			onUpdate: async ({ result, form, cancel }) => {
				if (result.type === 'success') {
					const resultInvokeUpdateProfile = await tryCatchAsVal(async () => {
						return (await invoke('update_profile', {
							data: {
								name: sanitizeInputText(form.data.name, 100)
							}
						})) as SuccessResponse<null>;
					});

					if (!resultInvokeUpdateProfile.success) {
						cancel();
						toast.error(resultInvokeUpdateProfile.error);
						return;
					}

					if (this.role === 'Admin') {
						const adminData = form.data as Infer<CompleteProfileAdminSchema> & { hospital?: string };
						const hospital = adminData.hospital;

						if (hospital) {
							await tryCatchAsVal(async () => {
								return (await invoke('update_registered_hospital_name', {
									hospitalName: sanitizeInputText(hospital, 100)
								})) as SuccessResponse<null>;
							});
						}
					}

					toast.success(resultInvokeUpdateProfile.data.status);
				}
			}
		});
	}
}
