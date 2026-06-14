# PGHD Smart Contract Testing

Dokumen ini menjelaskan skenario pengujian smart contract PGHD pada package Move `move/decmed`. Test otomatis berada pada:

- `move/decmed/tests/pghd_smart_contract_tests.move`
- wrapper test-only PGHD berada pada `move/decmed/sources/proxy.move`

## Prasyarat

- IOTA CLI tersedia.
- Dependency Move sudah pernah ter-resolve.
- Jalankan command dari root package Move:

```bash
cd /home/hackastic/Proglang/TugasAkhir/DecMed-PGHD/move/decmed
```

Gunakan `--skip-fetch-latest-git-deps` agar test tidak mencoba menulis/fetch ulang dependency git IOTA:

```bash
iota move test --skip-fetch-latest-git-deps
```

Expected output umum:

```text
Test result: OK. Total tests: 19; passed: 19; failed: 0
```

## Cara Menjalankan Test PGHD Saja

```bash
iota move test --skip-fetch-latest-git-deps pghd_smart_contract_tests
```

Atau satu test tertentu:

```bash
iota move test --skip-fetch-latest-git-deps test_sc_pghd_06_read_invalidated_entry
```

## Data Test yang Dibuat

Test menggunakan `iota::test_scenario` sehingga tidak membutuhkan object ID testnet. Semua value dibuat in-memory:

| Komponen | Value Test |
|---|---|
| Publisher | `@0xA` |
| Proxy | `@0xAAAA` |
| Patient 1 | `@0xAA` |
| Patient 2 | `@0xAA2` |
| Medical personnel | `@0xCC` |
| CID PGHD | `bafy-pghd-cid-{index}` |
| Hash cipher | `sha256-pghd-cipher-{index}` |
| Metadata PGHD | `encrypted-pghd-metadata-{index}` |

Karena test berjalan di unit test Move, tidak perlu mengambil value dari IOTA testnet, IPFS, PRE, atau Redis.

## Skenario dan Expected Output Teknis

| Kode | Skenario | Ekspektasi Balikan Fungsional | Expected Output Teknis |
|---|---|---|---|
| T-SC-PGHD01 | Proxy menyimpan metadata PGHD dan tenaga kesehatan dengan akses `READ_PGHD` membaca list/detail PGHD pasien. | Berhasil. | Test `PASS`; `get_pghd_list_test` mengembalikan 1 metadata; `get_pghd_test` mengembalikan index `0`, `prev_index = none`, `next_index = none`. |
| T-SC-PGHD02 | Tenaga kesehatan mencoba membaca daftar PGHD tanpa akses dari pasien. | Gagal karena akses tidak ada. | Abort `::decmed::proxy::EAccessNotFound` / code `4001`. |
| T-SC-PGHD03 | Tenaga kesehatan memiliki akses medical record biasa, tetapi mencoba membaca PGHD. | Gagal karena tipe akses salah. | Abort `::decmed::proxy::EInvalidAccessType` / code `4004`. |
| T-SC-PGHD04 | Tenaga kesehatan membaca PGHD setelah masa akses `READ_PGHD` 24 jam habis. | Gagal karena akses expired dan akses dihapus dari map. | Abort `::decmed::proxy::EAccessExpired` / code `4000`. |
| T-SC-PGHD05 | Tenaga kesehatan membuka index PGHD yang tidak ada. | Gagal karena entri PGHD tidak ditemukan. | Abort `::decmed::proxy::EPGHDRecordNotFound` / code `4008`. |
| T-SC-PGHD06 | Tenaga kesehatan membuka entri PGHD yang sudah diinvalidasi. | Gagal karena status entri bukan valid. | Abort `::decmed::proxy::EPGHDRecordInvalid` / code `4009`. |
| T-SC-PGHD07 | Tenaga kesehatan mencoba menginvalidasi CID PGHD yang tidak ada. | Gagal karena CID tidak ditemukan. | Abort `::decmed::proxy::EPGHDRecordNotFound` / code `4008`. |
| T-SC-PGHD08 | Proxy mencoba submit PGHD dengan metadata kosong. | Gagal karena metadata PGHD tidak valid. | Abort `::decmed::proxy::EInvalidPghdMetadata` / code `4007`. |
| T-SC-PGHD09 | Akses PGHD diberikan untuk Patient 1, tetapi dipakai membaca PGHD Patient 2. | Gagal karena akses bersifat spesifik per pasien. | Abort `::decmed::proxy::EAccessNotFound` / code `4001`. |

## Mapping Test Otomatis

| Kode | Nama Fungsi Test |
|---|---|
| T-SC-PGHD01 | `test_sc_pghd_01_submit_and_read_success` |
| T-SC-PGHD02 | `test_sc_pghd_02_read_without_access` |
| T-SC-PGHD03 | `test_sc_pghd_03_read_with_wrong_access_type` |
| T-SC-PGHD04 | `test_sc_pghd_04_read_after_access_expired` |
| T-SC-PGHD05 | `test_sc_pghd_05_read_missing_index` |
| T-SC-PGHD06 | `test_sc_pghd_06_read_invalidated_entry` |
| T-SC-PGHD07 | `test_sc_pghd_07_invalidate_missing_cid` |
| T-SC-PGHD08 | `test_sc_pghd_08_submit_empty_metadata` |
| T-SC-PGHD09 | `test_sc_pghd_09_read_different_patient_without_access` |

## Catatan Teknis

- `READ_PGHD` dipisahkan dari akses medical record biasa. Karena itu test T-SC-PGHD03 harus mengembalikan `EInvalidAccessType`.
- Expiry akses PGHD di kontrak adalah 24 jam dari `clock.timestamp_ms()` saat akses dibuat.
- PGHD invalidated tidak dihapus dari storage, tetapi tidak dikembalikan oleh `get_pghd_list` dan tidak bisa dibuka oleh `get_pghd`.
- Test ini tidak menguji enkripsi PRE atau IPFS. Integrity kriptografis PRE diuji oleh `scripts/non-functional/nf02_integrity.sh`.
