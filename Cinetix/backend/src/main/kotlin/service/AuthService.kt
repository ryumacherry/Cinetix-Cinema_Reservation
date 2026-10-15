package service

import exception.AutentikasiException
import exception.EmailSudahTerdaftarException
import exception.ValidasiException
import model.Pengguna
import repository.PenggunaRepository
import util.KodeGenerator
import util.PasswordHasher

class AuthService(private val repo: PenggunaRepository) {

    data class HasilLogin(val token: String, val pengguna: Pengguna)

    fun daftar(nama: String?, email: String?, password: String?): HasilLogin {
        val n = nama?.trim().orEmpty()
        val e = email?.trim()?.lowercase().orEmpty()
        val p = password.orEmpty()
        if (n.length < 3) throw ValidasiException("Nama minimal 3 huruf.")
        if (!EMAIL_REGEX.matches(e)) throw ValidasiException("Format email tidak valid.")
        if (p.length < 6) throw ValidasiException("Password minimal 6 karakter.")
        if (repo.cariByEmail(e) != null) throw EmailSudahTerdaftarException(e)

        repo.tambah(n, e, PasswordHasher.hash(p))
        return login(e, p)
    }

    fun login(email: String?, password: String?): HasilLogin {
        val akun = repo.cariByEmail(email?.trim()?.lowercase().orEmpty())
        if (akun == null || !PasswordHasher.cocok(password.orEmpty(), akun.passwordHash))
            throw AutentikasiException("Email atau password salah.")
        val token = KodeGenerator.token()
        repo.simpanSesi(token, akun.pengguna.id)
        return HasilLogin(token, akun.pengguna)
    }

    /** Mengambil pengguna dari header "Authorization: Bearer <token>". */
    fun dariToken(token: String?): Pengguna {
        if (token.isNullOrBlank()) throw AutentikasiException()
        return repo.penggunaDariSesi(token) ?: throw AutentikasiException("Sesi berakhir, silakan login ulang.")
    }

    fun logout(token: String?) {
        if (!token.isNullOrBlank()) repo.hapusSesi(token)
    }

    companion object {
        // sama dengan InputHelper.bacaEmail di dokumen desain
        val EMAIL_REGEX = Regex("^[\\w.+-]+@[\\w-]+\\.[\\w.]+$")
    }
}
