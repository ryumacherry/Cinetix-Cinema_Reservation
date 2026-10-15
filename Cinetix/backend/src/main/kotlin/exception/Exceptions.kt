package exception

/**
 * Induk semua exception bisnis CINETIX.
 * [statusHttp] dipakai Router untuk menentukan kode HTTP balasan ke klien (web).
 */
open class CinetixException(message: String, val statusHttp: Int = 400) : RuntimeException(message)

class KursiSudahDipesanException(kursi: String) :
    CinetixException("Kursi $kursi sudah dipesan orang lain.", 409)

class HariTidakValidException(message: String) : CinetixException(message, 400)

class PembayaranGagalException(message: String) : CinetixException(message, 400)

class DataTidakDitemukanException(apa: String) : CinetixException("$apa tidak ditemukan.", 404)

class ValidasiException(message: String) : CinetixException(message, 400)

class EmailSudahTerdaftarException(email: String) : CinetixException("Email $email sudah terdaftar.", 409)

class AutentikasiException(message: String = "Silakan login terlebih dahulu.") : CinetixException(message, 401)

class AksesDitolakException(message: String = "Hanya admin yang boleh mengakses fitur ini.") :
    CinetixException(message, 403)
