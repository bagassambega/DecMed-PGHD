#[test_only]
module decmed::pghd_smart_contract_tests;

use decmed::shared_tests::{
    patient_id,
    setup_data,
    setup_shared_objects,
};

use decmed::patient::{
    create_access_test,
};

use decmed::proxy::{
    get_pghd_list_test,
    get_pghd_test,
    invalidate_pghd_entry_test,
    submit_pghd_test,
};

use decmed::shared::{
    encode_patient_id,
    ProxyCap,
};

use decmed::std_struct_address_id::AddressId;
use decmed::std_struct_hospital_id_metadata::HospitalIdMetadata;
use decmed::std_struct_hospital_personnel_id_account::HospitalPersonnelIdAccount;
use decmed::std_struct_patient_id_account::PatientIdAccount;
use decmed::std_struct_patient_pghd_store::{
    PatientPghdStore,
    default as patient_pghd_store_default,
    destroy_for_testing as patient_pghd_store_destroy_for_testing,
};

use std::string::{Self, String};

use iota::clock::{Self, Clock};
use iota::test_scenario;

#[test_only]
const PUBLISHER_ADDR: address = @0xA;
#[test_only]
const PROXY_ADDR: address = @0xAAAA;
#[test_only]
const PATIENT_ADDR: address = @0xAA;
#[test_only]
const PATIENT_2_ADDR: address = @0xAA2;
#[test_only]
const MEDICAL_PERSONNEL_ADDR: address = @0xCC;

#[test_only]
fun pghd_cid(index: u64): String
{
    let mut cid = string::utf8(b"bafy-pghd-cid-");
    cid.append(index.to_string());
    cid
}

#[test_only]
fun pghd_h_cipher(index: u64): String
{
    let mut h_cipher = string::utf8(b"sha256-pghd-cipher-");
    h_cipher.append(index.to_string());
    h_cipher
}

#[test_only]
fun pghd_metadata(index: u64): String
{
    let mut metadata = string::utf8(b"encrypted-pghd-metadata-");
    metadata.append(index.to_string());
    metadata
}

#[test_only]
fun pghd_access_metadata(): vector<String>
{
    let mut metadata = vector::empty<String>();
    metadata.push_back(string::utf8(b"ReadPghdKFragAndAccessTokenMetadata"));
    metadata
}

#[test_only]
fun medical_access_metadata(): vector<String>
{
    let mut metadata = vector::empty<String>();
    metadata.push_back(string::utf8(b"ReadMedicalRecordAccessMetadata"));
    metadata.push_back(string::utf8(b"UpdateMedicalRecordAccessMetadata"));
    metadata
}

#[test_only]
fun create_access(
    clock: &Clock,
    hospital_personnel_address: address,
    metadata: vector<String>,
    scenario: &mut test_scenario::Scenario,
)
{
    let address_id = test_scenario::take_shared<AddressId>(scenario);
    let mut hospital_personnel_id_account = test_scenario::take_shared<HospitalPersonnelIdAccount>(scenario);
    let mut patient_id_account = test_scenario::take_shared<PatientIdAccount>(scenario);
    let hospital_id_metadata = test_scenario::take_shared<HospitalIdMetadata>(scenario);

    create_access_test(
        &address_id,
        clock,
        string::utf8(b"2026-06-14T00:00:00+07:00"),
        &hospital_id_metadata,
        hospital_personnel_address,
        &mut hospital_personnel_id_account,
        metadata,
        &mut patient_id_account,
        test_scenario::ctx(scenario),
    );

    test_scenario::return_shared(address_id);
    test_scenario::return_shared(hospital_personnel_id_account);
    test_scenario::return_shared(patient_id_account);
    test_scenario::return_shared(hospital_id_metadata);
}

#[test_only]
fun submit_one_pghd(
    clock: &Clock,
    patient_address: address,
    index: u64,
    patient_pghd_store: &mut PatientPghdStore,
    scenario: &mut test_scenario::Scenario,
)
{
    let address_id = test_scenario::take_shared<AddressId>(scenario);
    let patient_id_account = test_scenario::take_shared<PatientIdAccount>(scenario);
    let proxy_cap = test_scenario::take_shared<ProxyCap>(scenario);

    submit_pghd_test(
        &address_id,
        clock,
        pghd_cid(index),
        pghd_h_cipher(index),
        pghd_metadata(index),
        patient_address,
        &patient_id_account,
        patient_pghd_store,
        &proxy_cap,
            test_scenario::ctx(scenario),
        );

    test_scenario::return_shared(address_id);
    test_scenario::return_shared(patient_id_account);
    test_scenario::return_shared(proxy_cap);
}

#[test_only]
fun new_patient_pghd_store(
    patient_index: u64,
    scenario: &mut test_scenario::Scenario,
): PatientPghdStore
{
    patient_pghd_store_default(
        encode_patient_id(patient_id(patient_index)),
        test_scenario::ctx(scenario),
    )
}

#[test_only]
fun setup_pghd_scenario(): (test_scenario::Scenario, Clock)
{
    let mut scenario_val = test_scenario::begin(PUBLISHER_ADDR);
    let scenario = &mut scenario_val;
    let clock = clock::create_for_testing(test_scenario::ctx(scenario));

    setup_shared_objects(test_scenario::ctx(scenario));
    test_scenario::next_tx(scenario, PUBLISHER_ADDR);
    setup_data(scenario);

    (scenario_val, clock)
}

#[test]
// T-SC-PGHD01: proxy can store PGHD metadata and authorized medical personnel can list/read it.
fun test_sc_pghd_01_submit_and_read_success()
{
    let (mut scenario_val, mut clck) = setup_pghd_scenario();
    let scenario = &mut scenario_val;
    let mut patient_pghd_store = new_patient_pghd_store(1, scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    submit_one_pghd(&clck, PATIENT_ADDR, 1, &mut patient_pghd_store, scenario);

    test_scenario::next_tx(scenario, PATIENT_ADDR);
    create_access(&clck, MEDICAL_PERSONNEL_ADDR, pghd_access_metadata(), scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    {
        let address_id = test_scenario::take_shared<AddressId>(scenario);
        let mut hospital_personnel_id_account = test_scenario::take_shared<HospitalPersonnelIdAccount>(scenario);
        let patient_id_account = test_scenario::take_shared<PatientIdAccount>(scenario);
        let proxy_cap = test_scenario::take_shared<ProxyCap>(scenario);
        clck.set_for_testing(3 * 60 * 1000);

        let pghd_list = get_pghd_list_test(
            &address_id,
            &clck,
            MEDICAL_PERSONNEL_ADDR,
            &mut hospital_personnel_id_account,
            PATIENT_ADDR,
            &patient_id_account,
            &patient_pghd_store,
            &proxy_cap,
            test_scenario::ctx(scenario),
        );
        assert!(pghd_list.length() == 1, 0);

        let (_, returned_index, prev_index, next_index) = get_pghd_test(
            &address_id,
            &clck,
            MEDICAL_PERSONNEL_ADDR,
            &mut hospital_personnel_id_account,
            0,
            PATIENT_ADDR,
            &patient_id_account,
            &patient_pghd_store,
            &proxy_cap,
            test_scenario::ctx(scenario),
        );
        assert!(returned_index == 0, 0);
        assert!(prev_index.is_none(), 0);
        assert!(next_index.is_none(), 0);

        test_scenario::return_shared(address_id);
        test_scenario::return_shared(hospital_personnel_id_account);
        test_scenario::return_shared(patient_id_account);
        test_scenario::return_shared(proxy_cap);
    };
    patient_pghd_store_destroy_for_testing(patient_pghd_store);
    clck.destroy_for_testing();
    test_scenario::end(scenario_val);
}

#[test, expected_failure(abort_code = ::decmed::proxy::EAccessNotFound)]
// T-SC-PGHD02: medical personnel reads PGHD without patient-granted PGHD access.
fun test_sc_pghd_02_read_without_access()
{
    let (mut scenario_val, mut clck) = setup_pghd_scenario();
    let scenario = &mut scenario_val;
    let mut patient_pghd_store = new_patient_pghd_store(1, scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    submit_one_pghd(&clck, PATIENT_ADDR, 1, &mut patient_pghd_store, scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    {
        let address_id = test_scenario::take_shared<AddressId>(scenario);
        let mut hospital_personnel_id_account = test_scenario::take_shared<HospitalPersonnelIdAccount>(scenario);
        let patient_id_account = test_scenario::take_shared<PatientIdAccount>(scenario);
        let proxy_cap = test_scenario::take_shared<ProxyCap>(scenario);
        clck.set_for_testing(3 * 60 * 1000);

        let _ = get_pghd_list_test(
            &address_id,
            &clck,
            MEDICAL_PERSONNEL_ADDR,
            &mut hospital_personnel_id_account,
            PATIENT_ADDR,
            &patient_id_account,
            &patient_pghd_store,
            &proxy_cap,
            test_scenario::ctx(scenario),
        );

        test_scenario::return_shared(address_id);
        test_scenario::return_shared(hospital_personnel_id_account);
        test_scenario::return_shared(patient_id_account);
        test_scenario::return_shared(proxy_cap);
    };
    patient_pghd_store_destroy_for_testing(patient_pghd_store);
    clck.destroy_for_testing();
    test_scenario::end(scenario_val);
}

#[test, expected_failure(abort_code = ::decmed::proxy::EInvalidAccessType)]
// T-SC-PGHD03: medical personnel has ordinary medical-record access, not READ_PGHD.
fun test_sc_pghd_03_read_with_wrong_access_type()
{
    let (mut scenario_val, mut clck) = setup_pghd_scenario();
    let scenario = &mut scenario_val;
    let mut patient_pghd_store = new_patient_pghd_store(1, scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    submit_one_pghd(&clck, PATIENT_ADDR, 1, &mut patient_pghd_store, scenario);

    test_scenario::next_tx(scenario, PATIENT_ADDR);
    create_access(&clck, MEDICAL_PERSONNEL_ADDR, medical_access_metadata(), scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    {
        let address_id = test_scenario::take_shared<AddressId>(scenario);
        let mut hospital_personnel_id_account = test_scenario::take_shared<HospitalPersonnelIdAccount>(scenario);
        let patient_id_account = test_scenario::take_shared<PatientIdAccount>(scenario);
        let proxy_cap = test_scenario::take_shared<ProxyCap>(scenario);
        clck.set_for_testing(3 * 60 * 1000);

        let _ = get_pghd_list_test(
            &address_id,
            &clck,
            MEDICAL_PERSONNEL_ADDR,
            &mut hospital_personnel_id_account,
            PATIENT_ADDR,
            &patient_id_account,
            &patient_pghd_store,
            &proxy_cap,
            test_scenario::ctx(scenario),
        );

        test_scenario::return_shared(address_id);
        test_scenario::return_shared(hospital_personnel_id_account);
        test_scenario::return_shared(patient_id_account);
        test_scenario::return_shared(proxy_cap);
    };
    patient_pghd_store_destroy_for_testing(patient_pghd_store);
    clck.destroy_for_testing();
    test_scenario::end(scenario_val);
}

#[test, expected_failure(abort_code = ::decmed::proxy::EAccessExpired)]
// T-SC-PGHD04: medical personnel reads PGHD after 24-hour PGHD access expires.
fun test_sc_pghd_04_read_after_access_expired()
{
    let (mut scenario_val, mut clck) = setup_pghd_scenario();
    let scenario = &mut scenario_val;
    let mut patient_pghd_store = new_patient_pghd_store(1, scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    submit_one_pghd(&clck, PATIENT_ADDR, 1, &mut patient_pghd_store, scenario);

    test_scenario::next_tx(scenario, PATIENT_ADDR);
    create_access(&clck, MEDICAL_PERSONNEL_ADDR, pghd_access_metadata(), scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    {
        let address_id = test_scenario::take_shared<AddressId>(scenario);
        let mut hospital_personnel_id_account = test_scenario::take_shared<HospitalPersonnelIdAccount>(scenario);
        let patient_id_account = test_scenario::take_shared<PatientIdAccount>(scenario);
        let proxy_cap = test_scenario::take_shared<ProxyCap>(scenario);
        clck.set_for_testing((24 * 60 * 60 * 1000) + 1);

        let _ = get_pghd_list_test(
            &address_id,
            &clck,
            MEDICAL_PERSONNEL_ADDR,
            &mut hospital_personnel_id_account,
            PATIENT_ADDR,
            &patient_id_account,
            &patient_pghd_store,
            &proxy_cap,
            test_scenario::ctx(scenario),
        );

        test_scenario::return_shared(address_id);
        test_scenario::return_shared(hospital_personnel_id_account);
        test_scenario::return_shared(patient_id_account);
        test_scenario::return_shared(proxy_cap);
    };
    patient_pghd_store_destroy_for_testing(patient_pghd_store);
    clck.destroy_for_testing();
    test_scenario::end(scenario_val);
}

#[test, expected_failure(abort_code = ::decmed::proxy::EPGHDRecordNotFound)]
// T-SC-PGHD05: medical personnel opens a PGHD index that does not exist.
fun test_sc_pghd_05_read_missing_index()
{
    let (mut scenario_val, mut clck) = setup_pghd_scenario();
    let scenario = &mut scenario_val;
    let mut patient_pghd_store = new_patient_pghd_store(1, scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    submit_one_pghd(&clck, PATIENT_ADDR, 1, &mut patient_pghd_store, scenario);

    test_scenario::next_tx(scenario, PATIENT_ADDR);
    create_access(&clck, MEDICAL_PERSONNEL_ADDR, pghd_access_metadata(), scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    {
        let address_id = test_scenario::take_shared<AddressId>(scenario);
        let mut hospital_personnel_id_account = test_scenario::take_shared<HospitalPersonnelIdAccount>(scenario);
        let patient_id_account = test_scenario::take_shared<PatientIdAccount>(scenario);
        let proxy_cap = test_scenario::take_shared<ProxyCap>(scenario);
        clck.set_for_testing(3 * 60 * 1000);

        let (_, _, _, _) = get_pghd_test(
            &address_id,
            &clck,
            MEDICAL_PERSONNEL_ADDR,
            &mut hospital_personnel_id_account,
            99,
            PATIENT_ADDR,
            &patient_id_account,
            &patient_pghd_store,
            &proxy_cap,
            test_scenario::ctx(scenario),
        );

        test_scenario::return_shared(address_id);
        test_scenario::return_shared(hospital_personnel_id_account);
        test_scenario::return_shared(patient_id_account);
        test_scenario::return_shared(proxy_cap);
    };
    patient_pghd_store_destroy_for_testing(patient_pghd_store);
    clck.destroy_for_testing();
    test_scenario::end(scenario_val);
}

#[test, expected_failure(abort_code = ::decmed::proxy::EPGHDRecordInvalid)]
// T-SC-PGHD06: medical personnel opens a PGHD entry that has been invalidated.
fun test_sc_pghd_06_read_invalidated_entry()
{
    let (mut scenario_val, mut clck) = setup_pghd_scenario();
    let scenario = &mut scenario_val;
    let mut patient_pghd_store = new_patient_pghd_store(1, scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    submit_one_pghd(&clck, PATIENT_ADDR, 1, &mut patient_pghd_store, scenario);

    test_scenario::next_tx(scenario, PATIENT_ADDR);
    create_access(&clck, MEDICAL_PERSONNEL_ADDR, pghd_access_metadata(), scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    {
        let address_id = test_scenario::take_shared<AddressId>(scenario);
        let mut hospital_personnel_id_account = test_scenario::take_shared<HospitalPersonnelIdAccount>(scenario);
        let patient_id_account = test_scenario::take_shared<PatientIdAccount>(scenario);
        let proxy_cap = test_scenario::take_shared<ProxyCap>(scenario);
        clck.set_for_testing(3 * 60 * 1000);

        invalidate_pghd_entry_test(
            &address_id,
            &clck,
            MEDICAL_PERSONNEL_ADDR,
            &mut hospital_personnel_id_account,
            pghd_cid(1),
            string::utf8(b"hash mismatch"),
            PATIENT_ADDR,
            &patient_id_account,
            &mut patient_pghd_store,
            &proxy_cap,
            test_scenario::ctx(scenario),
        );

        let (_, _, _, _) = get_pghd_test(
            &address_id,
            &clck,
            MEDICAL_PERSONNEL_ADDR,
            &mut hospital_personnel_id_account,
            0,
            PATIENT_ADDR,
            &patient_id_account,
            &patient_pghd_store,
            &proxy_cap,
            test_scenario::ctx(scenario),
        );

        test_scenario::return_shared(address_id);
        test_scenario::return_shared(hospital_personnel_id_account);
        test_scenario::return_shared(patient_id_account);
        test_scenario::return_shared(proxy_cap);
    };
    patient_pghd_store_destroy_for_testing(patient_pghd_store);
    clck.destroy_for_testing();
    test_scenario::end(scenario_val);
}

#[test, expected_failure(abort_code = ::decmed::proxy::EPGHDRecordNotFound)]
// T-SC-PGHD07: medical personnel invalidates a PGHD CID that does not exist.
fun test_sc_pghd_07_invalidate_missing_cid()
{
    let (mut scenario_val, mut clck) = setup_pghd_scenario();
    let scenario = &mut scenario_val;
    let mut patient_pghd_store = new_patient_pghd_store(1, scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    submit_one_pghd(&clck, PATIENT_ADDR, 1, &mut patient_pghd_store, scenario);

    test_scenario::next_tx(scenario, PATIENT_ADDR);
    create_access(&clck, MEDICAL_PERSONNEL_ADDR, pghd_access_metadata(), scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    {
        let address_id = test_scenario::take_shared<AddressId>(scenario);
        let mut hospital_personnel_id_account = test_scenario::take_shared<HospitalPersonnelIdAccount>(scenario);
        let patient_id_account = test_scenario::take_shared<PatientIdAccount>(scenario);
        let proxy_cap = test_scenario::take_shared<ProxyCap>(scenario);
        clck.set_for_testing(3 * 60 * 1000);

        invalidate_pghd_entry_test(
            &address_id,
            &clck,
            MEDICAL_PERSONNEL_ADDR,
            &mut hospital_personnel_id_account,
            string::utf8(b"bafy-missing"),
            string::utf8(b"hash mismatch"),
            PATIENT_ADDR,
            &patient_id_account,
            &mut patient_pghd_store,
            &proxy_cap,
            test_scenario::ctx(scenario),
        );

        test_scenario::return_shared(address_id);
        test_scenario::return_shared(hospital_personnel_id_account);
        test_scenario::return_shared(patient_id_account);
        test_scenario::return_shared(proxy_cap);
    };
    patient_pghd_store_destroy_for_testing(patient_pghd_store);
    clck.destroy_for_testing();
    test_scenario::end(scenario_val);
}

#[test, expected_failure(abort_code = ::decmed::proxy::EInvalidPghdMetadata)]
// T-SC-PGHD08: proxy submits PGHD metadata with an empty metadata payload.
fun test_sc_pghd_08_submit_empty_metadata()
{
    let (mut scenario_val, clck) = setup_pghd_scenario();
    let scenario = &mut scenario_val;
    let mut patient_pghd_store = new_patient_pghd_store(1, scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    {
        let address_id = test_scenario::take_shared<AddressId>(scenario);
        let patient_id_account = test_scenario::take_shared<PatientIdAccount>(scenario);
        let proxy_cap = test_scenario::take_shared<ProxyCap>(scenario);

        submit_pghd_test(
            &address_id,
            &clck,
            pghd_cid(1),
            pghd_h_cipher(1),
            string::utf8(b""),
            PATIENT_ADDR,
            &patient_id_account,
            &mut patient_pghd_store,
            &proxy_cap,
            test_scenario::ctx(scenario),
        );

        test_scenario::return_shared(address_id);
        test_scenario::return_shared(patient_id_account);
        test_scenario::return_shared(proxy_cap);
    };
    patient_pghd_store_destroy_for_testing(patient_pghd_store);
    clck.destroy_for_testing();
    test_scenario::end(scenario_val);
}

#[test, expected_failure(abort_code = ::decmed::proxy::EAccessNotFound)]
// T-SC-PGHD09: PGHD access granted for one patient cannot be used for another patient.
fun test_sc_pghd_09_read_different_patient_without_access()
{
    let (mut scenario_val, mut clck) = setup_pghd_scenario();
    let scenario = &mut scenario_val;
    let mut patient_2_pghd_store = new_patient_pghd_store(2, scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    submit_one_pghd(&clck, PATIENT_2_ADDR, 1, &mut patient_2_pghd_store, scenario);

    test_scenario::next_tx(scenario, PATIENT_ADDR);
    create_access(&clck, MEDICAL_PERSONNEL_ADDR, pghd_access_metadata(), scenario);

    test_scenario::next_tx(scenario, PROXY_ADDR);
    {
        let address_id = test_scenario::take_shared<AddressId>(scenario);
        let mut hospital_personnel_id_account = test_scenario::take_shared<HospitalPersonnelIdAccount>(scenario);
        let patient_id_account = test_scenario::take_shared<PatientIdAccount>(scenario);
        let proxy_cap = test_scenario::take_shared<ProxyCap>(scenario);
        clck.set_for_testing(3 * 60 * 1000);

        let _ = get_pghd_list_test(
            &address_id,
            &clck,
            MEDICAL_PERSONNEL_ADDR,
            &mut hospital_personnel_id_account,
            PATIENT_2_ADDR,
            &patient_id_account,
            &patient_2_pghd_store,
            &proxy_cap,
            test_scenario::ctx(scenario),
        );

        test_scenario::return_shared(address_id);
        test_scenario::return_shared(hospital_personnel_id_account);
        test_scenario::return_shared(patient_id_account);
        test_scenario::return_shared(proxy_cap);
    };
    patient_pghd_store_destroy_for_testing(patient_2_pghd_store);
    clck.destroy_for_testing();
    test_scenario::end(scenario_val);
}
