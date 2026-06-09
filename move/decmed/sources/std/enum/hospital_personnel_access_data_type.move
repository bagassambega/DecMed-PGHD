module decmed::std_enum_hospital_personnel_access_data_type;

public enum HospitalPersonnelAccessDataType has copy, drop, store {
    Administrative,
    Medical,
    Pghd,
}

public(package) fun administrative(): HospitalPersonnelAccessDataType {
    HospitalPersonnelAccessDataType::Administrative
}

public(package) fun medical(): HospitalPersonnelAccessDataType {
    HospitalPersonnelAccessDataType::Medical
}

public(package) fun pghd(): HospitalPersonnelAccessDataType {
    HospitalPersonnelAccessDataType::Pghd
}
