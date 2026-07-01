package com.hackastic.decmed.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ─── Domain types ──────────────────────────────────────────────────────────────

private data class TosClause(val heading: String, val body: String)

private data class TosSection(
    val id: String,
    val title: String,
    val summary: String,
    val clauses: List<TosClause>,
    val checkboxLabel: String
)

// ─── Terms content ─────────────────────────────────────────────────────────────

private val TERMS_SECTIONS = listOf(
    TosSection(
        id = "general",
        title = "Ketentuan Umum & Kerangka Persetujuan PGHD",
        summary = "Ketentuan ini mengatur penggunaan aplikasi DecMed untuk pengumpulan, pengiriman, dan pengelolaan " +
            "Patient-Generated Health Data (PGHD) dalam ekosistem Rekam Medis Elektronik terdesentralisasi. " +
            "Dengan melanjutkan, Anda menyatakan telah membaca setiap bagian secara penuh dan menyetujui seluruh ketentuan di bawah ini.",
        clauses = listOf(
            TosClause(
                "1.1 Penerimaan Ketentuan",
                "Dengan mengakses atau menggunakan aplikasi DecMed, Anda menyatakan berusia minimal 18 tahun " +
                    "(atau memiliki persetujuan orang tua/wali yang dapat diverifikasi) dan bersedia terikat oleh " +
                    "ketentuan ini. Jika Anda tidak setuju, Anda tidak diperbolehkan menggunakan aplikasi ini."
            ),
            TosClause(
                "1.2 Lingkup Pengumpulan PGHD",
                "DecMed mengumpulkan Patient-Generated Health Data (PGHD) dari sensor perangkat Android Anda " +
                    "(termasuk Health Connect/wearable, sensor bawaan, dan input manual) setelah Anda mengaktifkan " +
                    "mode pengumpulan secara eksplisit. Data ini digunakan untuk mendukung rekam medis longitudinal " +
                    "Anda dan alur kerja pelayanan kesehatan yang telah Anda otorisasi."
            ),
            TosClause(
                "1.3 Tanggung Jawab Akun",
                "Anda bertanggung jawab menjaga kerahasiaan PIN lokal dan seluruh " +
                    "kredensial autentikasi Anda. Aktivitas yang dilakukan di bawah akun Anda menjadi tanggung jawab Anda. " +
                    "Anda harus segera menghubungi DecMed jika menduga adanya akses tidak sah."
            ),
            TosClause(
                "1.4 Akurasi Informasi",
                "Anda menyetujui untuk memberikan informasi yang akurat, terkini, dan lengkap selama registrasi " +
                    "dan penggunaan platform."
            ),
            TosClause(
                "1.5 Sifat Final Persetujuan",
                "Setelah Anda menerima Ketentuan ini dan persetujuan PGHD, keputusan penerimaan tersebut bersifat " +
                    "final untuk konteks akun Anda dan tidak dapat dicabut atau diubah melalui kontrol dalam aplikasi. " +
                    "Jika Anda tidak setuju dengan kondisi ini, Anda harus menolak sebelum melanjutkan."
            ),
            TosClause(
                "1.6 Pembatasan Tanggung Jawab",
                "DecMed disediakan sebagaimana adanya (as-is). Sejauh yang diizinkan oleh aturan dan perjanjian " +
                    "yang berlaku, pengembang dan operator DecMed tidak bertanggung jawab atas kerugian tidak " +
                    "langsung, insidental, khusus, atau konsekuensial yang timbul dari penggunaan platform."
            ),
            TosClause(
                "1.7 Perubahan Ketentuan",
                "DecMed dapat memperbarui ketentuan ini sewaktu-waktu. Perubahan material akan dikomunikasikan " +
                    "melalui notifikasi dalam aplikasi. Penggunaan yang berlanjut setelah pembaruan tersebut " +
                    "menunjukkan penerimaan terhadap ketentuan yang telah direvisi."
            )
        ),
        checkboxLabel = "Saya telah membaca dan menyetujui Ketentuan Umum & Kerangka Persetujuan PGHD di atas."
    ),
    TosSection(
        id = "data-collection",
        title = "Pengumpulan, Pengiriman, dan Penyimpanan PGHD",
        summary = "Bagian ini menjelaskan cara DecMed mengumpulkan data dari perangkat Anda, memproses data " +
            "secara lokal sebelum pengiriman, dan menyimpannya secara terenkripsi pada infrastruktur terdesentralisasi.",
        clauses = listOf(
            TosClause(
                "2.1 Sumber Data PGHD",
                "DecMed dapat mengumpulkan PGHD dari: (a) Health Connect dan perangkat wearable yang didukung, " +
                    "(b) sensor Android yang memiliki pemetaan ke tipe data kesehatan, dan (c) input manual yang " +
                    "Anda masukkan secara langsung. Pengumpulan baru dimulai setelah Anda mengaktifkan mode " +
                    "pengumpulan secara eksplisit dan mengonfigurasi sensor yang diinginkan."
            ),
            TosClause(
                "2.2 Penyimpanan Lokal Sementara (Offline-First)",
                "PGHD yang dikumpulkan disimpan terlebih dahulu di database lokal (Room DB) pada perangkat Anda " +
                    "sebelum dikirimkan. Pendekatan offline-first ini memastikan data tetap tersedia meskipun " +
                    "perangkat Anda sedang tidak terhubung ke jaringan. Backup otomatis Android dinonaktifkan " +
                    "untuk mencegah restorasi state terenkripsi ke lingkungan yang tidak kompatibel."
            ),
            TosClause(
                "2.3 Mekanisme Batching",
                "Data lokal dikelompokkan menjadi batch secara periodik (default setiap 15 menit) atau lebih " +
                    "awal jika ukuran payload mencapai batas tertentu. Health Connect disinkronkan secara " +
                    "otomatis setiap 3 menit ketika mode pengumpulan aktif. Sensor disampling default setiap " +
                    "1 menit. Mekanisme ini memastikan pengiriman data yang efisien dan meminimalkan beban jaringan."
            ),
            TosClause(
                "2.4 Enkripsi Sebelum Pengiriman",
                "Setiap batch PGHD dienkripsi secara lokal di perangkat Anda menggunakan AES-GCM sebelum " +
                    "dikirimkan ke server. Kunci enkripsi AES dibungkus menggunakan Proxy Re-Encryption (PRE) " +
                    "dengan kunci publik PGHD Anda. Plaintext PGHD tidak pernah dikirimkan ke PRE, IPFS, " +
                    "maupun IOTA dalam bentuk yang dapat dibaca."
            ),
            TosClause(
                "2.5 Integritas Data",
                "Setelah enkripsi, hash SHA-256 dari ciphertext (h_cipher) dihitung dan ditandatangani secara " +
                    "digital menggunakan kunci penandatanganan PGHD Anda (ECDSA). Tanda tangan ini memungkinkan " +
                    "verifikasi keaslian dan integritas data oleh server PRE dan tenaga kesehatan penerima."
            ),
            TosClause(
                "2.6 Penyimpanan Terdesentralisasi",
                "Ciphertext PGHD yang telah dienkripsi disimpan di IPFS (InterPlanetary File System) dan " +
                    "diidentifikasi melalui Content Identifier (CID). Metadata PGHD—termasuk CID, hash " +
                    "ciphertext, kapsul PRE, kunci terenkripsi, tanda tangan, dan status validitas—dicatat " +
                    "pada smart contract IOTA sebagai sumber kebenaran (source of truth)."
            ),
            TosClause(
                "2.7 Klasifikasi PGHD",
                "PGHD disimpan dengan metadata klasifikasi khusus agar dapat dibedakan dari rekam medis " +
                    "klinis yang dibuat oleh tenaga kesehatan."
            ),
            TosClause(
                "2.8 Retensi dan Integritas Jangka Panjang",
                "PGHD dan artefak terkait dipertahankan sesuai kebijakan retensi platform untuk menjaga " +
                    "kontinuitas, reprodusibilitas, dan integritas audit. Status PGHD dapat berubah menjadi " +
                    "valid atau tidak valid; invalidasi dilakukan melalui transaksi baru tanpa menghapus " +
                    "riwayat lama di blockchain."
            ),
            TosClause(
                "2.9 Persetujuan Pengumpulan Bersifat Final",
                "Dengan menerima bagian ini, Anda mengakui bahwa persetujuan pengumpulan PGHD bersifat final " +
                    "untuk status ketentuan yang telah diterima dan tidak dapat dicabut melalui kontrol dalam " +
                    "aplikasi setelah penerimaan."
            )
        ),
        checkboxLabel = "Saya menyetujui pengumpulan, pengiriman, dan penyimpanan PGHD sebagaimana dijelaskan di atas, termasuk pengakuan sifat final persetujuan."
    ),
    TosSection(
        id = "data-access",
        title = "Pengaksesan PGHD oleh Tenaga Kesehatan",
        summary = "Bagian ini menjelaskan bagaimana tenaga kesehatan yang telah Anda otorisasi dapat mengakses " +
            "dan menggunakan PGHD Anda dalam ekosistem DecMed melalui mekanisme kontrol akses berbasis kapabilitas.",
        clauses = listOf(
            TosClause(
                "2.1 Mekanisme Pemberian Akses (Grant)",
                "Anda memberikan akses PGHD kepada tenaga kesehatan secara eksplisit dengan memindai kode QR " +
                    "milik mereka menggunakan aplikasi Android DecMed. Kode QR hanya memuat alamat IOTA dan " +
                    "kunci publik PRE tenaga kesehatan; identitas lengkap (nama, rumah sakit) diverifikasi " +
                    "dari smart contract IOTA, bukan dari kode QR."
            ),
            TosClause(
                "2.2 Infrastruktur Kontrol Akses",
                "Saat Anda memberikan akses, aplikasi membuat sepasang kunci PRE khusus (Data PRE keypair), " +
                    "key re-encryption fragment (kfrag), dan seed terenkripsi untuk tenaga kesehatan tersebut. " +
                    "Material kunci ini disimpan sementara di Redis dengan durasi default 24 jam. Kapabilitas " +
                    "akses juga dicatat pada smart contract IOTA (CapBAC) sebagai bukti otorisasi yang dapat diaudit."
            ),
            TosClause(
                "2.3 Proses Pembacaan PGHD oleh Tenaga Kesehatan",
                "Tenaga kesehatan mengakses PGHD Anda melalui client rumah sakit (Hospital Client) yang " +
                    "berkomunikasi dengan PRE backend. PRE memvalidasi akses melalui smart contract IOTA, " +
                    "mengambil ciphertext dari IPFS, memverifikasi hash, membangun cfrag, lalu " +
                    "mengembalikan material terenkripsi ke client. Dekripsi dilakukan sepenuhnya secara " +
                    "lokal di sisi client tenaga kesehatan setelah verifikasi hash dan tanda tangan berhasil."
            ),
            TosClause(
                "2.4 Pemisahan Akses PGHD dan RME",
                "Akses PGHD menggunakan tujuan (purpose) ReadPghd yang terpisah dari tujuan akses rekam " +
                    "medis klinis (Read/Update). Tenaga kesehatan yang memiliki akses PGHD tidak serta-merta " +
                    "memiliki akses ke rekam medis klinis Anda, dan sebaliknya."
            ),
            TosClause(
                "2.5 Pencabutan Akses (Revoke)",
                "Anda dapat mencabut akses PGHD yang sebelumnya diberikan melalui aplikasi Android. " +
                    "Pencabutan akan menghapus material kunci PRE dari Redis. Status akses yang paling " +
                    "akurat bersumber dari IOTA, PRE, dan Redis—bukan hanya dari daftar lokal di aplikasi Anda."
            ),
            TosClause(
                "2.6 Akuntabilitas Institusi dan Personel",
                "Setiap organisasi dan akun personel yang berinteraksi dengan data Anda dapat diidentifikasi " +
                    "dan diaudit melalui smart contract IOTA. Semua peristiwa akses dan modifikasi direkam " +
                    "untuk keperluan pengawasan dan verifikasi."
            ),
            TosClause(
                "2.7 Pembatasan Penggunaan Data Klinis",
                "PGHD yang dihasilkan pasien tidak diverifikasi secara klinis oleh tenaga kesehatan sebelum " +
                    "diunggah. Tenaga kesehatan wajib memverifikasi dan mendekripsi PGHD di sisi client mereka " +
                    "sebelum menggunakannya untuk keputusan klinis. Data yang gagal verifikasi hash atau " +
                    "tanda tangan tidak boleh digunakan dan dapat diinvalidasi."
            )
        ),
        checkboxLabel = "Saya menyetujui pemberian akses PGHD kepada tenaga kesehatan yang saya otorisasi melalui mekanisme yang dijelaskan di atas."
    ),
    TosSection(
        id = "data-processing",
        title = "Pengolahan dan Pengelolaan PGHD",
        summary = "Bagian ini menjelaskan bagaimana DecMed memproses, mengelola, dan menjaga keamanan PGHD " +
            "Anda sepanjang siklus hidupnya—mulai dari pengumpulan hingga penyimpanan jangka panjang.",
        clauses = listOf(
            TosClause(
                "3.1 Pemrosesan Kriptografi",
                "Seluruh operasi kriptografi PGHD—termasuk enkripsi AES-GCM, pembungkusan kunci PRE/Umbral, " +
                    "pembuatan kfrag, dan penandatanganan digital—dilakukan secara lokal di perangkat Anda " +
                    "menggunakan library native (libdecmed_crypto.so dan libdecmed_iota.so). Private key Anda " +
                    "tidak pernah dikirimkan ke server PRE, IPFS, maupun dicatat di smart contract."
            ),
            TosClause(
                "3.2 Verifikasi Sisi Server (PRE)",
                "Server PRE hanya menerima payload terenkripsi. Saat menerima batch PGHD, PRE melakukan: " +
                    "(a) verifikasi hash ciphertext terhadap h_cipher yang dikirimkan, (b) pengambilan kunci " +
                    "publik penandatanganan PGHD Anda dari IOTA, (c) verifikasi tanda tangan digital terhadap " +
                    "hash ciphertext, (d) upload ciphertext ke IPFS, dan (e) pencatatan metadata ke smart " +
                    "contract IOTA."
            ),
            TosClause(
                "3.3 Data Provenance",
                "Sistem DecMed menjamin keterlacakan asal-usul data (data provenance) melalui pencatatan " +
                    "metadata pada smart contract IOTA dan payload IPFS. Setiap batch PGHD memiliki metadata " +
                    "yang mencakup CID, hash ciphertext, kapsul PRE, tanda tangan digital, timestamp, dan " +
                    "indeks batch. Riwayat ini tidak dapat dimanipulasi karena dicatat di ledger terdesentralisasi."
            ),
            TosClause(
                "3.4 Pengelolaan Kunci dan Pemisahan",
                "Sistem menggunakan pemisahan kunci yang ketat: (a) Kunci IOTA pasien untuk autentikasi dan " +
                    "otorisasi, (b) Kunci PRE PGHD pasien untuk re-enkripsi kunci AES, (c) Kunci penandatanganan " +
                    "PGHD pasien untuk tanda tangan digital (public key dicatat di IOTA), dan (d) Data PRE " +
                    "keypair per-grant untuk setiap otorisasi akses. Seluruh kunci private disimpan di " +
                    "secure storage lokal perangkat Anda."
            ),
            TosClause(
                "3.5 Invalidasi Data",
                "Anda atau tenaga kesehatan yang berwenang dapat menginvalidasi batch PGHD yang terbukti " +
                    "rusak atau tidak sah. Invalidasi dilakukan melalui transaksi baru di smart contract " +
                    "IOTA yang mengubah status PGHD menjadi tidak valid. Pin IPFS untuk ciphertext terkait " +
                    "dapat dihapus setelah invalidasi. Riwayat invalidasi tetap tercatat di blockchain."
            ),
            TosClause(
                "3.6 Retry dan Ketersediaan",
                "Batch yang gagal dikirim disimpan lokal dan dijadwalkan ulang oleh WorkManager saat jaringan " +
                    "tersedia. Mekanisme pengamanan (PghdBatchCreationGuard) mencegah pembuatan batch ganda " +
                    "dari data yang sama. Anda dapat memicu pengiriman ulang secara manual melalui antarmuka " +
                    "aplikasi."
            ),
            TosClause(
                "3.7 Kontrol Sebelum Pengumpulan Dimulai",
                "Anda memilih sensor dan data kesehatan yang dikumpulkan melalui layar konfigurasi sebelum " +
                    "pengumpulan dimulai. Tindakan memulai pengumpulan secara manual merupakan instruksi " +
                    "eksplisit Anda untuk memulai pengambilan data di bawah ketentuan yang telah disetujui."
            )
        ),
        checkboxLabel = "Saya memahami dan menyetujui mekanisme pengolahan dan pengelolaan PGHD, termasuk pemrosesan kriptografi, data provenance, dan pengelolaan kunci sebagaimana dijelaskan di atas."
    ),
    TosSection(
        id = "notifications",
        title = "Notifikasi Akses dan Perubahan PGHD",
        summary = "Bagian ini menjelaskan cara DecMed memberi tahu Anda ketika rekam medis yang mencakup PGHD " +
            "diakses atau dimodifikasi, serta hak Anda untuk mempersoalkan akses yang tidak sah.",
        clauses = listOf(
            TosClause(
                "4.1 Notifikasi Akses",
                "Anda akan menerima notifikasi ketika tenaga kesehatan yang berwenang mengakses rekam medis " +
                    "Anda, termasuk informasi identitas aktor (dari smart contract IOTA), tipe akses, " +
                    "cakupan data, dan timestamp kejadian."
            ),
            TosClause(
                "4.2 Notifikasi Modifikasi",
                "Anda akan menerima notifikasi ketika rekam medis dibuat atau diperbarui oleh tenaga kesehatan " +
                    "berwenang, mencakup informasi perubahan yang dilakukan, inisiator tindakan, dan waktu kejadian."
            ),
            TosClause(
                "4.3 Akses Pengawasan Sistem",
                "Dalam keperluan pengawasan operasional atau tinjauan platform yang diwajibkan, akses semacam " +
                    "itu tetap dapat diaudit dan akan ditampilkan kepada Anda melalui pengalaman notifikasi " +
                    "dan log akses dalam aplikasi jika tersedia."
            ),
            TosClause(
                "4.4 Akses Auditor Pihak Ketiga",
                "Jika auditor independen dilibatkan untuk memverifikasi integritas sistem, akses mereka " +
                    "dikendalikan, dapat diaudit, dan dibatasi sesuai cakupan tinjauan yang disepakati."
            ),
            TosClause(
                "4.5 Pengiriman Notifikasi",
                "Notifikasi disampaikan melalui saluran dalam aplikasi dan log akses. Anda bertanggung jawab " +
                    "untuk meninjau notifikasi ini secara berkala."
            ),
            TosClause(
                "4.6 Pelaporan Akses Tidak Sah",
                "Jika Anda yakin terdapat akses yang tidak sah atau tidak tepat terhadap data Anda, Anda dapat " +
                    "mengajukan laporan melalui saluran dukungan resmi untuk investigasi dan tindak lanjut."
            )
        ),
        checkboxLabel = "Saya memahami dan menyetujui menerima notifikasi ketika rekam medis terkait PGHD diakses atau dimodifikasi sebagaimana dijelaskan di atas."
    )
)

// ─── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun TermsOfServiceScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    // Per-section read/agreed state
    val sectionScrolled = remember { mutableStateMapOf<String, Boolean>() }
    val sectionAgreed = remember { mutableStateMapOf<String, Boolean>() }
    var masterAgreed by remember { mutableStateOf(false) }
    var showDeclineDialog by remember { mutableStateOf(false) }

    // First section expanded by default, matching web behaviour
    var expandedSectionId by remember { mutableStateOf<String?>(TERMS_SECTIONS.firstOrNull()?.id) }

    val allSectionsAgreed = TERMS_SECTIONS.all { sectionAgreed[it.id] == true }
    val canProceed = allSectionsAgreed && masterAgreed

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Sticky header — respects status bar insets ──────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // windowInsetsPadding(WindowInsets.statusBars) pushes content
                        // below the system status bar / notification panel so the
                        // header title is never obscured on any Android device.
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Syarat & Ketentuan",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "DecMed — Rekam Medis Elektronik Terdesentralisasi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Scrollable body ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Harap baca setiap bagian di bawah ini dengan saksama. Anda harus menggulir hingga " +
                        "bagian bawah setiap bagian dan mencentang kotak persetujuan sebelum dapat melanjutkan. " +
                        "Semua bagian memerlukan persetujuan eksplisit Anda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TERMS_SECTIONS.forEachIndexed { index, section ->
                    val isScrolled = sectionScrolled[section.id] == true
                    val isAgreed = sectionAgreed[section.id] == true
                    val isExpanded = expandedSectionId == section.id

                    TermsSectionCard(
                        section = section,
                        sectionNumber = index + 1,
                        isScrolled = isScrolled,
                        isAgreed = isAgreed,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedSectionId = if (isExpanded) null else section.id
                        },
                        onScrolled = { sectionScrolled[section.id] = true },
                        onAgreeChange = { checked ->
                            sectionAgreed[section.id] = checked
                            // If any section is unchecked, also uncheck master
                            if (!checked) masterAgreed = false
                        }
                    )
                }

                // ── Master confirmation card ───────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Checkbox(
                                checked = masterAgreed,
                                enabled = allSectionsAgreed,
                                onCheckedChange = { masterAgreed = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = "Saya telah membaca, memahami, dan menyetujui seluruh Syarat & Ketentuan " +
                                        "di atas, termasuk efek persetujuan PGHD yang bersifat final setelah penerimaan.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (allSectionsAgreed)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!allSectionsAgreed) {
                                    Text(
                                        text = "(setujui semua bagian di atas untuk mengaktifkan)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDeclineDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Tolak")
                            }
                            Button(
                                onClick = onAccept,
                                enabled = canProceed,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Setuju & Lanjutkan")
                            }
                        }
                    }
                }

                // Footer
                Text(
                    text = "DecMed — Sistem Rekam Medis Elektronik Terdesentralisasi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }

    // ── Decline dialog ─────────────────────────────────────────────────────
    if (showDeclineDialog) {
        AlertDialog(
            onDismissRequest = { showDeclineDialog = false },
            title = {
                Text(
                    text = "Tidak Dapat Melanjutkan",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Anda harus menerima seluruh Syarat & Ketentuan untuk menggunakan aplikasi DecMed. " +
                            "Persetujuan pengumpulan PGHD dalam alur ini bersifat final setelah diterima.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Jika Anda memiliki pertanyaan terkait ketentuan tertentu, silakan hubungi tim " +
                            "dukungan kami untuk klarifikasi sebelum membuat keputusan.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showDeclineDialog = false }) {
                    Text("Kembali ke Ketentuan")
                }
            }
        )
    }
}

// ─── Section card ──────────────────────────────────────────────────────────────

@Composable
private fun TermsSectionCard(
    section: TosSection,
    sectionNumber: Int,
    isScrolled: Boolean,
    isAgreed: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onScrolled: () -> Unit,
    onAgreeChange: (Boolean) -> Unit
) {
    val contentScrollState = rememberScrollState()

    // Detect reaching the bottom of the clause content.
    // Use a small threshold (8px) to account for sub-pixel rounding.
    val reachedBottom by remember(contentScrollState.value, contentScrollState.maxValue) {
        derivedStateOf {
            contentScrollState.maxValue > 0 &&
                contentScrollState.value >= contentScrollState.maxValue - 8
        }
    }
    LaunchedEffect(reachedBottom) {
        if (reachedBottom) onScrolled()
    }
    // If the content is short enough that no scrolling is needed, mark read once expanded.
    LaunchedEffect(isExpanded, contentScrollState.maxValue) {
        if (isExpanded && contentScrollState.maxValue == 0) onScrolled()
    }

    val badgeText: String
    val badgeContainerColor: androidx.compose.ui.graphics.Color
    val badgeContentColor: androidx.compose.ui.graphics.Color

    when {
        isAgreed -> {
            badgeText = "Disetujui"
            badgeContainerColor = MaterialTheme.colorScheme.tertiaryContainer
            badgeContentColor = MaterialTheme.colorScheme.onTertiaryContainer
        }
        isScrolled -> {
            badgeText = "Dibaca"
            badgeContainerColor = MaterialTheme.colorScheme.secondaryContainer
            badgeContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
        else -> {
            badgeText = "Belum Dibaca"
            badgeContainerColor = MaterialTheme.colorScheme.surfaceVariant
            badgeContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            // ── Section header row (always visible) ───────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bagian $sectionNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Status badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = badgeContainerColor
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeContentColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                androidx.compose.material3.Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Tutup bagian" else "Buka bagian",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Collapsible body ──────────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(150))
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    // Scrollable clause content — fixed height forces the user to scroll
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .verticalScroll(contentScrollState)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = section.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )

                        section.clauses.forEach { clause ->
                            Text(
                                text = clause.heading,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = clause.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    // ── Per-section agreement checkbox ─────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (!isScrolled) {
                            Text(
                                text = "Gulir ke bawah untuk mengaktifkan",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Checkbox(
                                checked = isAgreed,
                                enabled = isScrolled,
                                onCheckedChange = onAgreeChange
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = section.checkboxLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isScrolled)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
