package api

import model.Pengguna

// Semua field request nullable + default, supaya JSON yang tidak lengkap
// menghasilkan pesan validasi yang jelas (bukan NullPointerException).

data class DaftarRequest(val nama: String? = null, val email: String? = null, val password: String? = null)
data class LoginRequest(val email: String? = null, val password: String? = null)
data class BookingRequest(val jadwalId: Int? = null, val kursi: List<String>? = null)
data class BayarRequest(val metode: String? = null, val bank: String? = null, val noKartu: String? = null)
data class HargaRequest(val harga: Double? = null)

data class PenggunaDto(val id: Int, val nama: String, val email: String, val peran: String) {
    companion object {
        fun dari(p: Pengguna) = PenggunaDto(p.id, p.nama, p.email, p.peran.name)
    }
}

data class LoginResponse(val token: String, val pengguna: PenggunaDto)
data class PesanResponse(val pesan: String, val id: Int? = null)
