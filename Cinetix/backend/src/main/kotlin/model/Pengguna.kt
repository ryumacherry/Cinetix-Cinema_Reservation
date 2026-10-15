package model

enum class Peran { PELANGGAN, ADMIN }

abstract class Pengguna(val id: Int, val nama: String, val email: String) {
    abstract val peran: Peran
    abstract fun bolehAksesAdmin(): Boolean
}

class Pelanggan(id: Int, nama: String, email: String) : Pengguna(id, nama, email) {
    override val peran = Peran.PELANGGAN
    override fun bolehAksesAdmin() = false
}

class Admin(id: Int, nama: String, email: String) : Pengguna(id, nama, email) {
    override val peran = Peran.ADMIN
    override fun bolehAksesAdmin() = true
}

object PenggunaFactory {
    fun buat(id: Int, nama: String, email: String, peran: String): Pengguna =
        if (peran.equals("ADMIN", true)) Admin(id, nama, email) else Pelanggan(id, nama, email)
}
