module decmed::std_struct_patient_pghd_metadata;

use std::string::Self;
use std::string::String;

public struct PatientPghdMetadata has copy, drop, store {
    index: u64,
    cid: String,
    h_cipher: String,
    metadata: String,
    status: u8,
    failure_reason: String,
    timestamp: u64,
}

public(package) fun new(
    index: u64,
    cid: String,
    h_cipher: String,
    metadata: String,
    timestamp: u64,
): PatientPghdMetadata
{
    PatientPghdMetadata {
        index,
        cid,
        h_cipher,
        metadata,
        status: 0,
        failure_reason: string::utf8(b""),
        timestamp,
    }
}

public(package) fun borrow_index(
    self: &PatientPghdMetadata,
): u64
{
    self.index
}

public(package) fun borrow_metadata(
    self: &PatientPghdMetadata,
): &String
{
    &self.metadata
}

public(package) fun borrow_cid(
    self: &PatientPghdMetadata,
): &String
{
    &self.cid
}

public(package) fun borrow_status(
    self: &PatientPghdMetadata,
): u8
{
    self.status
}

public(package) fun invalidate(
    self: &mut PatientPghdMetadata,
    failure_reason: String,
)
{
    self.status = 1;
    self.failure_reason = failure_reason;
}

#[test_only]
public(package) fun default(): PatientPghdMetadata
{
    PatientPghdMetadata {
        index: 0,
        cid: string::utf8(b"cid"),
        h_cipher: string::utf8(b"h_cipher"),
        metadata: string::utf8(b"PghdMetadata"),
        status: 0,
        failure_reason: string::utf8(b""),
        timestamp: 0,
    }
}
