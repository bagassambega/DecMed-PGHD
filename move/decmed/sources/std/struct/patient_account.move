module decmed::std_struct_patient_account;

use decmed::std_struct_patient_access_log::PatientAccessLog;
use decmed::std_struct_patient_administrative_metadata::PatientAdministrativeMetadata;
#[test_only]
use decmed::std_struct_patient_administrative_metadata::default as patient_administrative_metadata_default;
use decmed::std_struct_patient_medical_metadata::PatientMedicalMetadata;
use decmed::std_struct_patient_pghd_metadata::PatientPghdMetadata;

#[test_only]
use iota::table_vec::Self;
use iota::table_vec::TableVec;
#[test_only]
use std::string::Self;
use std::string::String;

public struct PatientAccount has store {
    access_log: TableVec<PatientAccessLog>,
    address: address,
    administrative_metadata: PatientAdministrativeMetadata,
    is_profile_completed: bool,
    medical_metadata: TableVec<PatientMedicalMetadata>,
    pghd_metadata: TableVec<PatientPghdMetadata>,
    pghd_store_id: ID,
    pghd_public_key: String,
}

public(package) fun new(
    access_log: TableVec<PatientAccessLog>,
    address: address,
    administrative_metadata: PatientAdministrativeMetadata,
    is_profile_completed: bool,
    medical_metadata: TableVec<PatientMedicalMetadata>,
    pghd_metadata: TableVec<PatientPghdMetadata>,
    pghd_store_id: ID,
    pghd_public_key: String,
): PatientAccount
{
    PatientAccount {
    	access_log,
        address,
    	administrative_metadata,
        is_profile_completed,
    	medical_metadata,
        pghd_metadata,
        pghd_store_id,
        pghd_public_key,
    }
}

public(package) fun borrow_access_log(
    self: &PatientAccount,
): &TableVec<PatientAccessLog>
{
    &self.access_log
}

public(package) fun borrow_mut_access_log(
    self: &mut PatientAccount,
): &mut TableVec<PatientAccessLog>
{
    &mut self.access_log
}

public(package) fun borrow_administrative_metadata(
    self: &PatientAccount,
): &PatientAdministrativeMetadata
{
    &self.administrative_metadata
}

public(package) fun borrow_mut_administrative_metadata(
    self: &mut PatientAccount,
): &mut PatientAdministrativeMetadata
{
    &mut self.administrative_metadata
}

public(package) fun set_administrative_metadata(
    self: &mut PatientAccount,
    administrative_metadata: PatientAdministrativeMetadata,
)
{
    self.administrative_metadata = administrative_metadata;
}

public(package) fun borrow_is_profile_completed(
    self: &PatientAccount,
): bool
{
    self.is_profile_completed
}

public(package) fun set_is_profile_completed(
    self: &mut PatientAccount,
    is_profile_completed: bool,
)
{
    self.is_profile_completed = is_profile_completed;
}

public(package) fun borrow_medical_metadata(
    self: &PatientAccount,
): &TableVec<PatientMedicalMetadata>
{
    &self.medical_metadata
}

public(package) fun borrow_mut_medical_metadata(
    self: &mut PatientAccount,
): &mut TableVec<PatientMedicalMetadata>
{
    &mut self.medical_metadata
}

public(package) fun borrow_pghd_metadata(
    self: &PatientAccount,
): &TableVec<PatientPghdMetadata>
{
    &self.pghd_metadata
}

public(package) fun borrow_mut_pghd_metadata(
    self: &mut PatientAccount,
): &mut TableVec<PatientPghdMetadata>
{
    &mut self.pghd_metadata
}

public(package) fun borrow_pghd_public_key(
    self: &PatientAccount,
): &String
{
    &self.pghd_public_key
}

public(package) fun set_pghd_public_key(
    self: &mut PatientAccount,
    pghd_public_key: String,
)
{
    self.pghd_public_key = pghd_public_key;
}

public(package) fun borrow_pghd_store_id(
    self: &PatientAccount,
): ID
{
    self.pghd_store_id
}

#[test_only]
public(package) fun default(
    address: address,
    ctx: &mut TxContext,
): PatientAccount
{
    PatientAccount {
    	access_log: table_vec::empty<PatientAccessLog>(ctx),
    	address,
    	administrative_metadata: patient_administrative_metadata_default(),
    	is_profile_completed: false,
    	medical_metadata: table_vec::empty<PatientMedicalMetadata>(ctx),
        pghd_metadata: table_vec::empty<PatientPghdMetadata>(ctx),
        pghd_store_id: object::id_from_address(@0x0),
        pghd_public_key: string::utf8(b""),
    }
}
