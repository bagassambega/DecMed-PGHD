module decmed::std_struct_patient_pghd_store;

use decmed::std_struct_patient_pghd_metadata::PatientPghdMetadata;

use iota::table_vec::{Self, TableVec};

use std::string::String;

public struct PatientPghdStore has key {
    id: UID,
    patient_id: String,
    metadata: TableVec<PatientPghdMetadata>,
}

public(package) fun new(
    patient_id: String,
    ctx: &mut TxContext,
): PatientPghdStore
{
    PatientPghdStore {
        id: object::new(ctx),
        patient_id,
        metadata: table_vec::empty<PatientPghdMetadata>(ctx),
    }
}

public(package) fun borrow_id(
    self: &PatientPghdStore,
): &UID
{
    &self.id
}

public(package) fun borrow_patient_id(
    self: &PatientPghdStore,
): &String
{
    &self.patient_id
}

public(package) fun borrow_metadata(
    self: &PatientPghdStore,
): &TableVec<PatientPghdMetadata>
{
    &self.metadata
}

public(package) fun borrow_mut_metadata(
    self: &mut PatientPghdStore,
): &mut TableVec<PatientPghdMetadata>
{
    &mut self.metadata
}

public(package) fun share(
    store: PatientPghdStore,
)
{
    transfer::share_object(store);
}

#[test_only]
public(package) fun destroy_for_testing(
    store: PatientPghdStore,
)
{
    let PatientPghdStore {
        id,
        patient_id: _,
        metadata,
    } = store;
    metadata.drop();
    id.delete();
}

#[test_only]
public(package) fun default(
    patient_id: String,
    ctx: &mut TxContext,
): PatientPghdStore
{
    PatientPghdStore {
        id: object::new(ctx),
        patient_id,
        metadata: table_vec::empty<PatientPghdMetadata>(ctx),
    }
}
