# Class Diagram, ERD, dan Arsitektur

GitHub merender diagram Mermaid di bawah ini secara otomatis.

## Arsitektur
```mermaid
flowchart LR
    W["Versi web<br/>HTML/CSS/JS"] -- "HTTP/JSON (fetch)" --> B["Backend API<br/>Kotlin OOP"]
    B -- "JDBC" --> C[("MySQL 8")]
    subgraph Backend
      direction TB
      R["api/ (Router + Routes)"] --> S["service/ (logika bisnis)"]
      S --> P["repository/ (JDBC)"]
      S --> M["model/ (OOP)"]
    end
```

## Class diagram domain (model/)
```mermaid
classDiagram
    direction TB
    class Diskon {
        <<interface>>
        +persenDiskon: Double
        +hitungDiskon(jumlahTiket, subtotal) Double
        +MIN_TIKET = 5
    }
    class Studio {
        <<abstract>>
        +id: Int
        +nama: String
        +kapasitas: Int
        +ruang: String
        #hargaDasar: Double
        +kursiPerBaris: Int
        +deskripsiPengalaman() String
        +hargaTiket(akhirPekan) Double
        +hitungDiskon(jumlahTiket, subtotal) Double
        +bonus(jumlahTiket) String
        +buatDenah() List~KursiDenah~
        +validasiDenah()
    }
    class Reguler {
        +kursiPerBaris = 10
        +persenDiskon = 6
        +jenisLayar: String
    }
    class Premiere {
        +kursiPerBaris = 8
        +persenDiskon = 8
    }
    class Suites {
        +kursiPerBaris = 6
        +persenDiskon = 10
        -pesananLayanan: List~String~
        +pesanLayananKursi(item)
    }
    Diskon <|.. Studio
    Studio <|-- Reguler
    Studio <|-- Premiere
    Studio <|-- Suites

    class KursiDenah {
        <<data>>
        +kode: String
        +baris: String
        +nomor: Int
        +section: DEPAN/BELAKANG
        +sisi: KIRI/KANAN
    }
    class Section {
        <<enumeration>>
        DEPAN
        BELAKANG
    }
    class Sisi {
        <<enumeration>>
        KIRI
        KANAN
    }
    Studio ..> KursiDenah : buatDenah()
    KursiDenah ..> Section
    KursiDenah ..> Sisi

    class Pembayaran {
        <<abstract>>
        +nominal: Double
        +status: StatusBayar
        +metode: String
        +kode: String
        +biayaAdmin() Double
        +totalBayar() Double
        +proses() Boolean
        +bayar() Boolean
    }
    class Qris {
        +payload() String
    }
    class TransferBank {
        +bank: String
        +nomorVirtualAccount(ref) String
    }
    class KartuKredit {
        -noKartu: String
        +nomorTersamar() String
    }
    Pembayaran <|-- Qris
    Pembayaran <|-- TransferBank
    Pembayaran <|-- KartuKredit

    class Pengguna {
        <<abstract>>
        +id
        +nama
        +email
        +peran: Peran
        +bolehAksesAdmin() Boolean
    }
    class Pelanggan
    class Admin
    Pengguna <|-- Pelanggan
    Pengguna <|-- Admin

    class Cetakable {
        <<interface>>
        +isiBarcode() String
        +cetak() String
    }
    class Tiket {
        <<data>>
        +kodeTiket
        +judulFilm
        +kursi
        +diskon
    }
    Cetakable <|.. Tiket

    class StudioFactory {
        <<object>>
        +buat(tipe, id, kapasitas, ruang, harga) Studio
    }
    class PembayaranFactory {
        <<object>>
        +buat(kode, nominal, bank, noKartu) Pembayaran
    }
    StudioFactory ..> Studio
    PembayaranFactory ..> Pembayaran
```

## Class diagram service & repository
```mermaid
classDiagram
    direction LR
    class CrudRepository~T~ {
        <<interface>>
        +semua() List~T~
        +cari(id) T
        +tambah(item) Int
        +ubah(id, item)
        +hapus(id)
    }
    class JadwalRepository {
        <<interface>>
        +cariJadwal(filter)
        +kursiTerisi(jadwalId)
    }
    class KursiRepository {
        <<interface>>
        +denah(studioId) List~KursiDenah~
        +simpanDenah(studioId, denah)
        +studioTanpaDenah()
    }
    class BookingRepository {
        <<interface>>
        +simpanBaru(data) Int
        +tandaiLunas(id, kodeTiket)
        +batalkan(id)
        +laporan()
    }
    CrudRepository <|-- JadwalRepository
    JadwalRepository <|.. JdbcJadwalRepository
    KursiRepository <|.. JdbcKursiRepository
    BookingRepository <|.. JdbcBookingRepository
    KursiRepository <|.. FakeKursiRepository : unit test
    BookingRepository <|.. FakeBookingRepository : unit test

    class JadwalService {
        +tanggalTersedia()
        +validasiTanggal()
        +cariTersedia()
        +denahKursi(jadwalId)
        +pastikanDenahLengkap()
        +generateOtomatis()
    }
    class BookingService {
        +buat(penggunaId, jadwalId, kursi)
        +detail()
        +batal()
        +bersihkanKedaluwarsa()
    }
    class PembayaranService {
        +mulai()
        +konfirmasi()
    }
    class AuthService {
        +daftar()
        +login()
        +dariToken()
    }
    class AdminService {
        +entitas(nama) SumberDayaAdmin
        +hargaStudio()
        +ubahHarga()
    }
    class SumberDayaAdmin~T~ {
        +daftar()
        +tambah(json)
        +ubah(id, json)
        +hapus(id)
    }

    JadwalService --> JadwalRepository
    JadwalService --> KursiRepository
    BookingService --> JadwalService
    BookingService --> KursiRepository
    BookingService --> BookingRepository
    PembayaranService --> BookingService
    AdminService --> SumberDayaAdmin
    SumberDayaAdmin --> CrudRepository
```

## ERD (MySQL, 11 tabel)
```mermaid
erDiagram
    KOTA ||--o{ CINEMA : memiliki
    CINEMA ||--o{ STUDIO : memiliki
    HARGA_STUDIO ||--o{ STUDIO : "harga per tipe"
    STUDIO ||--|{ KURSI : "denah studio"
    FILM ||--o{ JADWAL : diputar
    STUDIO ||--o{ JADWAL : dipakai
    PENGGUNA ||--o{ BOOKING : memesan
    PENGGUNA ||--o{ SESI : login
    JADWAL ||--o{ BOOKING : dipesan
    BOOKING ||--|{ KURSI_DIPESAN : mengunci
    JADWAL ||--o{ KURSI_DIPESAN : "UNIQUE(jadwal_id,kursi_id)"
    KURSI ||--o{ KURSI_DIPESAN : dipilih

    KOTA {
        int id PK
        string nama
    }
    CINEMA {
        int id PK
        string nama
        string alamat
        int kota_id FK
    }
    HARGA_STUDIO {
        enum tipe PK
        decimal harga_dasar
    }
    STUDIO {
        int id PK
        int cinema_id FK
        string nama
        enum tipe
        int kapasitas
    }
    KURSI {
        int id PK
        int studio_id FK
        string kode
        char baris
        int nomor
        enum section
        enum sisi
    }
    FILM {
        int id PK
        string judul
        string genre
        int durasi
        string rating_usia
        enum status
        date tanggal_rilis
        string poster_url "lokasi file poster di server"
    }
    JADWAL {
        int id PK
        int film_id FK
        int studio_id FK
        date tanggal
        time jam
    }
    PENGGUNA {
        int id PK
        string nama
        string email
        string password_hash
        enum peran
    }
    SESI {
        string token PK
        int pengguna_id FK
    }
    BOOKING {
        int id PK
        int pengguna_id FK
        int jadwal_id FK
        string kursi
        decimal subtotal_tiket
        decimal diskon
        decimal total
        string metode_bayar
        enum status
        string kode_tiket
    }
    KURSI_DIPESAN {
        int id PK
        int booking_id FK
        int jadwal_id FK
        int kursi_id FK
    }
```

## Peta konsep OOP
| Konsep | Penerapan |
|---|---|
| Abstract class | `Studio`, `Pembayaran`, `Pengguna` |
| Inheritance | `Reguler/Premiere/Suites`, `Qris/TransferBank/KartuKredit`, `Pelanggan/Admin` |
| Polymorphism | `hargaTiket()`, `persenDiskon`/`hitungDiskon()`, `kursiPerBaris`, `bonus()` per studio; `biayaAdmin()`/`proses()` per pembayaran |
| Interface | `Diskon` (diimplementasikan `Studio`), `Cetakable`, `CrudRepository<T>`, `KursiRepository`, dll. |
| Encapsulation | `status` bayar hanya berubah lewat `bayar()`; `pesananLayanan` Suites `private` |
| Data class | `Film`, `Cinema`, `Jadwal`, `Kursi`, `KursiDenah`, `Tiket`, `BookingDetail` |
| Enum | `TipeStudio`, `Section`, `Sisi`, `StatusBayar` |
| Generics | `CrudRepository<T>`, `SumberDayaAdmin<T, F>` |
| Factory | `StudioFactory`, `PembayaranFactory`, `PenggunaFactory` |
| Custom exception | `KursiSudahDipesanException`, `HariTidakValidException`, `PembayaranGagalException`, `DataTidakDitemukanException` |
