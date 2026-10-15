'use strict';
/*
 * CINETIX versi web
 * Satu halaman (SPA) tanpa framework, memakai REST API backend Kotlin.
 * Disajikan langsung oleh backend Kotlin: buka http://localhost:8080/ di browser.
 */

// =====================================================================
// Sesi & state
// =====================================================================
const simpan = {
  get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
  set(k, v) { try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k, v); } catch (e) { /* abaikan */ } }
};

// Sesi login disimpan di sessionStorage: otomatis hilang saat tab/jendela ditutup (reload tetap login).
// Pilihan kota tetap di localStorage karena hanya preferensi.
const sesi = {
  get(k) { try { return sessionStorage.getItem(k); } catch (e) { return null; } },
  set(k, v) { try { if (v == null) sessionStorage.removeItem(k); else sessionStorage.setItem(k, v); } catch (e) { /* abaikan */ } }
};
simpan.set('cinetix_token', null);       // buang sisa sesi dari versi lama yang disimpan permanen
simpan.set('cinetix_pengguna', null);

const S = {
  token: sesi.get('cinetix_token'),
  pengguna: JSON.parse(sesi.get('cinetix_pengguna') || 'null'),
  kotaId: Number(simpan.get('cinetix_kota') || 2),
  kota: [],
  tabFilm: 'NOW_PLAYING',
  tanggal: null,                          // tanggal jadwal yang dipilih
  kursi: { jadwalId: null, dipilih: [] }, // kursi yang dipilih di layar denah
  timer: null                             // hitung mundur pembayaran
};

function simpanSesi(token, pengguna) {
  S.token = token;
  S.pengguna = pengguna;
  sesi.set('cinetix_token', token);
  sesi.set('cinetix_pengguna', pengguna ? JSON.stringify(pengguna) : null);
}

// =====================================================================
// Pemanggil REST API
// =====================================================================
async function api(path, opsi = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (S.token) headers.Authorization = 'Bearer ' + S.token;
  let res;
  try {
    res = await fetch(path, {
      method: opsi.method || 'GET',
      headers,
      body: opsi.body !== undefined ? JSON.stringify(opsi.body) : undefined
    });
  } catch (e) {
    throw new Error('Tidak bisa terhubung ke server Cinetix. Pastikan backend berjalan.');
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 401 && !path.startsWith('/api/auth')) {
      simpanSesi(null, null);
      location.hash = '#/login';
    }
    throw new Error(data.pesan || 'Kesalahan server (' + res.status + ')');
  }
  return data;
}

/** Mengunggah file mentah (bukan JSON), dipakai untuk poster film. */
async function apiUnggah(path, berkas) {
  const headers = { 'Content-Type': berkas.type || 'application/octet-stream' };
  if (S.token) headers.Authorization = 'Bearer ' + S.token;
  let res;
  try {
    res = await fetch(path, { method: 'POST', headers, body: berkas });
  } catch (e) {
    throw new Error('Tidak bisa terhubung ke server Cinetix. Pastikan backend berjalan.');
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.pesan || 'Kesalahan server (' + res.status + ')');
  return data;
}

/** Memperkecil gambar pilihan admin (sisi terpanjang maks 1280 px) lalu menjadikannya JPEG, supaya jauh di bawah batas 3 MB. */
function kecilkanGambar(file, sisiMaks = 1280) {
  return new Promise((ok, gagal) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      const skala = Math.min(1, sisiMaks / Math.max(img.width, img.height));
      const c = document.createElement('canvas');
      c.width = Math.max(1, Math.round(img.width * skala));
      c.height = Math.max(1, Math.round(img.height * skala));
      const g = c.getContext('2d');
      g.fillStyle = '#fff';                       // PNG transparan tidak menjadi hitam
      g.fillRect(0, 0, c.width, c.height);
      g.drawImage(img, 0, 0, c.width, c.height);
      c.toBlob(b => (b ? ok(b) : gagal(new Error('Gambar tidak bisa diproses.'))), 'image/jpeg', 0.85);
    };
    img.onerror = () => { URL.revokeObjectURL(url); gagal(new Error('File yang dipilih bukan gambar.')); };
    img.src = url;
  });
}

// =====================================================================
// Helper tampilan
// =====================================================================
const $ = (sel, el = document) => el.querySelector(sel);
const $$ = (sel, el = document) => Array.from(el.querySelectorAll(sel));

function esc(v) {
  return String(v == null ? '' : v).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function rupiah(n) { return 'Rp' + Math.round(Number(n) || 0).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.'); }

const HARI = ['Minggu', 'Senin', 'Selasa', 'Rabu', 'Kamis', 'Jumat', 'Sabtu'];
const BULAN = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];
function keTanggal(iso) { const [y, m, d] = iso.split('-').map(Number); return new Date(y, m - 1, d); }
function tglPanjang(iso) { const t = keTanggal(iso); return `${HARI[t.getDay()]}, ${t.getDate()} ${BULAN[t.getMonth()]} ${t.getFullYear()}`; }
function tglPendek(iso) { const t = keTanggal(iso); return `${t.getDate()} ${BULAN[t.getMonth()]}`; }
function labelHari(iso, i) { return i === 0 ? 'Hari ini' : i === 1 ? 'Besok' : HARI[keTanggal(iso).getDay()]; }
function durasi(m) { return m >= 60 ? `${Math.floor(m / 60)} jam ${m % 60} mnt` : `${m} mnt`; }
function waktu(teks) { if (!teks) return '-'; const [tgl, jam] = teks.split(' '); return `${tglPendek(tgl)} ${tgl.slice(0, 4)}, ${(jam || '').slice(0, 5)}`; }
function mmss(detik) { return String(Math.floor(detik / 60)).padStart(2, '0') + ':' + String(detik % 60).padStart(2, '0'); }
function urutKursi(list) { return list.slice().sort((a, b) => a[0].localeCompare(b[0]) || Number(a.slice(1)) - Number(b.slice(1))); }

const PALET = [['#c62828', '#4a148c'], ['#1565c0', '#004d40'], ['#ef6c00', '#b71c1c'], ['#2e7d32', '#1b5e20'],
  ['#6a1b9a', '#283593'], ['#37474f', '#111111'], ['#ad1457', '#4e342e']];
/**
 * Poster film. Jika admin sudah mengunggah gambar, `url` = lokasi file di server sendiri (mis. /uploads/posters/film-3-17.jpg).
 * Jika belum ada / gagal dimuat, yang tampil poster pengganti (gradasi warna + inisial judul).
 */
function poster(judul, id, besar, url) {
  const [a, b] = PALET[id % PALET.length];
  const inisial = judul.split(' ').filter(Boolean)
    .slice(0, 2).map(k => k[0].toUpperCase()).join('');

  // Nama file lama seperti "poster_1.jpg" diarahkan ke folder uploads/posters.
  // URL upload yang sudah lengkap tetap digunakan apa adanya.
  const urlPoster = url
    ? (url.startsWith('/uploads/posters/')
        ? url
        : '/uploads/posters/' + url.replace(/^\/+/, '').split('/').pop())
    : '';

  const gambar = urlPoster
    ? `<img src="${esc(urlPoster)}" alt="Poster ${esc(judul)}" loading="lazy" onerror="this.remove()">`
    : '';

  return `<div class="poster${besar ? ' besar' : ''}" style="background:linear-gradient(160deg,${a},${b})"><span>${esc(inisial)}</span><small>CINETIX</small>${gambar}</div>`;
}

function toast(pesan, jenis = 'info') {
  const t = $('#toast');
  t.textContent = pesan;
  t.className = 'toast ' + jenis;
  t.hidden = false;
  clearTimeout(toast.t);
  toast.t = setTimeout(() => { t.hidden = true; }, 3500);
}

function tampil(html) {
  $('#app').innerHTML = html;
  try { window.scrollTo(0, 0); } catch (e) { /* abaikan */ }
}

function kelompok(list, kunci) {
  const m = new Map();
  list.forEach(x => { const k = kunci(x); if (!m.has(k)) m.set(k, []); m.get(k).push(x); });
  return m;
}

/** Nilai isian form berdasarkan atribut name (untuk radio: nilai yang dipilih). */
function nilai(f, nama) {
  const el = $$(`[name="${nama}"]`, f);
  if (el.length && el[0].type === 'radio') { const c = el.find(x => x.checked); return c ? c.value : ''; }
  return el.length ? el[0].value : '';
}

function bukaModal(judul, isiForm, onSimpan) {
  const m = $('#modal');
  m.innerHTML = `<form class="kartu modal-isi"><h3>${esc(judul)}</h3>${isiForm}
    <div class="aksi"><button type="button" class="btn garis" data-tutup>Batal</button><button class="btn" type="submit">Simpan</button></div></form>`;
  m.hidden = false;
  const f = $('form', m);
  $('[data-tutup]', m).onclick = tutupModal;
  f.onsubmit = async e => {
    e.preventDefault();
    const btn = $('button[type=submit]', f);
    btn.disabled = true;
    try { await onSimpan(f); tutupModal(); } catch (err) { toast(err.message, 'gagal'); btn.disabled = false; }
  };
}
function tutupModal() { const m = $('#modal'); m.hidden = true; m.innerHTML = ''; }

// =====================================================================
// Header & router
// =====================================================================
function renderHeader() {
  const nav = $('#nav');
  const kw = $('#kota-wrap');
  if (!S.pengguna) { nav.innerHTML = ''; kw.innerHTML = ''; return; }
  const admin = S.pengguna.peran === 'ADMIN';
  // Riwayat pembelian hanya untuk pelanggan; admin tidak memesan tiket
  const menu = [['#/film', 'Film'], ['#/bioskop', 'Bioskop'], ...(admin ? [] : [['#/riwayat', 'Riwayat']]), ['#/profil', 'Profil']];
  if (admin) menu.push(['#/admin/laporan', 'Admin']);
  const aktif = (location.hash.split('/')[1] || '').replace('jadwal', 'film').replace('kursi', 'film');
  nav.innerHTML = menu.map(([href, label]) => `<a href="${href}" class="${href.split('/')[1] === aktif ? 'aktif' : ''}">${label}</a>`).join('');
  kw.innerHTML = `<select id="pilih-kota" aria-label="Pilih kota">${S.kota.map(k =>
    `<option value="${k.id}" ${k.id === S.kotaId ? 'selected' : ''}>${esc(k.nama)}</option>`).join('')}</select>`;
  $('#pilih-kota').onchange = e => { S.kotaId = Number(e.target.value); simpan.set('cinetix_kota', S.kotaId); jalankan(); };
}

const RUTE = [
  { pola: /^#\/login$/, hal: halLogin, publik: true },
  { pola: /^#\/daftar$/, hal: halDaftar, publik: true },
  { pola: /^#\/film$/, hal: halFilm },
  { pola: /^#\/film\/(\d+)$/, hal: halFilmDetail },
  { pola: /^#\/jadwal\/(\d+)$/, hal: halJadwal },
  { pola: /^#\/bioskop$/, hal: halBioskop },
  { pola: /^#\/bioskop\/(\d+)$/, hal: halBioskopDetail },
  { pola: /^#\/kursi\/(\d+)$/, hal: halKursi },
  { pola: /^#\/bayar\/(\d+)$/, hal: halBayar },
  { pola: /^#\/tiket\/(\d+)$/, hal: halTiket },
  { pola: /^#\/riwayat$/, hal: halRiwayat, pelanggan: true },
  { pola: /^#\/profil$/, hal: halProfil },
  { pola: /^#\/admin\/(\w+)$/, hal: halAdmin, admin: true }
];

async function jalankan() {
  clearInterval(S.timer);
  tutupModal();
  const hash = location.hash || '#/film';
  const r = RUTE.find(x => x.pola.test(hash));
  if (!r) { location.hash = '#/film'; return; }
  if (!r.publik && !S.token) { location.hash = '#/login'; return; }
  if (r.publik && S.token) { location.hash = '#/film'; return; }
  if (r.admin && (!S.pengguna || S.pengguna.peran !== 'ADMIN')) { location.hash = '#/film'; return; }
  if (r.pelanggan && S.pengguna && S.pengguna.peran === 'ADMIN') { location.hash = '#/film'; return; }
  if (S.token && !S.kota.length) {
    try {
      S.kota = await api('/api/kota');
      if (S.kota.length && !S.kota.some(k => k.id === S.kotaId)) S.kotaId = S.kota[0].id;
    } catch (e) { /* pesan error tampil di halaman */ }
  }
  renderHeader();
  tampil('<p class="muat">Memuat…</p>');
  try {
    await r.hal(...hash.match(r.pola).slice(1));
  } catch (e) {
    tampil(`<div class="kosong"><h2>Oops!</h2><p>${esc(e.message)}</p><button class="btn" id="coba">Coba lagi</button></div>`);
    $('#coba').onclick = jalankan;
  }
}

// =====================================================================
// Halaman: login & daftar
// =====================================================================
async function halLogin() {
  tampil(`<div class="auth"><div class="logo-besar">CX</div><h1>Cinetix</h1><p class="redup">Pesan tiket bioskop lebih cepat</p>
    <form id="f-login" class="kartu form">
      <label>Email<input name="email" type="email" required autocomplete="username"></label>
      <label>Password<input name="password" type="password" required autocomplete="current-password"></label>
      <button class="btn penuh" type="submit">Masuk</button>
      <p class="tengah"><a href="#/daftar">Belum punya akun? Daftar</a></p>
    </form>
    <div class="kartu info"><b>Akun demo</b><br>Pelanggan: budi@mail.com / budi123<br>Admin: admin@cinetix.id / admin123</div></div>`);
  const f = $('#f-login');
  f.onsubmit = async e => {
    e.preventDefault();
    const btn = $('button', f);
    btn.disabled = true; btn.textContent = 'Memproses…';
    try {
      const h = await api('/api/auth/login', { method: 'POST', body: { email: nilai(f, 'email').trim(), password: nilai(f, 'password') } });
      simpanSesi(h.token, h.pengguna);
      location.hash = '#/film';
    } catch (err) {
      toast(err.message, 'gagal');
      btn.disabled = false; btn.textContent = 'Masuk';
    }
  };
}

async function halDaftar() {
  tampil(`<div class="auth"><h1>Daftar Akun</h1>
    <form id="f-daftar" class="kartu form">
      <label>Nama lengkap<input name="nama" required minlength="3"></label>
      <label>Email<input name="email" type="email" required></label>
      <label>Password (min. 6 karakter)<input name="password" type="password" required minlength="6"></label>
      <label>Ulangi password<input name="ulang" type="password" required></label>
      <button class="btn penuh" type="submit">Daftar</button>
      <p class="tengah"><a href="#/login">Sudah punya akun? Masuk</a></p>
    </form></div>`);
  const f = $('#f-daftar');
  f.onsubmit = async e => {
    e.preventDefault();
    if (nilai(f, 'password') !== nilai(f, 'ulang')) { toast('Konfirmasi password tidak sama.', 'gagal'); return; }
    try {
      const h = await api('/api/auth/daftar', { method: 'POST', body: { nama: nilai(f, 'nama').trim(), email: nilai(f, 'email').trim(), password: nilai(f, 'password') } });
      simpanSesi(h.token, h.pengguna);
      location.hash = '#/film';
    } catch (err) { toast(err.message, 'gagal'); }
  };
}

// =====================================================================
// Halaman: film
// =====================================================================
async function halFilm() {
  const [tayang, segera] = await Promise.all([api('/api/film?status=NOW_PLAYING'), api('/api/film?status=COMING_SOON')]);
  const kartu = f => `<a class="kartu-film" href="#/film/${f.id}">${poster(f.judul, f.id, false, f.posterUrl)}<div class="isi">
      <b>${esc(f.judul)}</b><span class="redup kecil">${esc(f.genre)} • ${durasi(f.durasi)}</span>
      ${f.status === 'COMING_SOON' && f.tanggalRilis ? `<span class="redup kecil">Rilis ${tglPanjang(f.tanggalRilis)}</span>` : ''}
      <span><span class="label">${esc(f.ratingUsia)}</span></span></div></a>`;
  const grid = list => list.length ? `<div class="grid-film">${list.map(kartu).join('')}</div>` : '<p class="kosong">Belum ada film.</p>';
  tampil(`<div class="tabs"><button data-tab="NOW_PLAYING">Sedang Tayang</button><button data-tab="COMING_SOON">Segera Tayang</button></div>
    <section data-isi="NOW_PLAYING">${grid(tayang)}</section><section data-isi="COMING_SOON">${grid(segera)}</section>`);
  const pilih = tab => {
    S.tabFilm = tab;
    $$('[data-tab]').forEach(b => b.classList.toggle('aktif', b.dataset.tab === tab));
    $$('[data-isi]').forEach(s => { s.hidden = s.dataset.isi !== tab; });
  };
  $$('[data-tab]').forEach(b => { b.onclick = () => pilih(b.dataset.tab); });
  pilih(S.tabFilm);
}

async function halFilmDetail(id) {
  const f = await api('/api/film/' + id);
  tampil(`<a class="kembali" href="#/film">← Kembali</a>
    <div class="detail-film">${poster(f.judul, f.id, true, f.posterUrl)}<div>
      <h1>${esc(f.judul)}</h1>
      <p><span class="label">${esc(f.ratingUsia)}</span> <span class="label">${esc(f.genre)}</span> <span class="label">${durasi(f.durasi)}</span></p>
      <h3>Sinopsis</h3><p>${esc(f.sinopsis || '-')}</p>
      ${S.pengguna && S.pengguna.peran === 'ADMIN' ? ''
        : f.status === 'NOW_PLAYING'
          ? `<a class="btn" href="#/jadwal/${f.id}">Pesan Tiket</a>`
          : `<button class="btn" disabled>Tayang mulai ${f.tanggalRilis ? tglPanjang(f.tanggalRilis) : 'segera'}</button>`}
    </div></div>`);
}

// =====================================================================
// Jadwal (dipakai halaman jadwal film & detail bioskop)
// =====================================================================
function htmlStudio(list) {
  const j0 = list[0];
  const jam = list.slice().sort((a, b) => a.jam.localeCompare(b.jam)).map(j => j.sisaKursi > 0
    ? `<a class="jam" href="#/kursi/${j.id}"><b>${esc(j.jam)}</b><small>${j.sisaKursi} kursi</small></a>`
    : `<span class="jam penuh"><b>${esc(j.jam)}</b><small>Penuh</small></span>`).join('');
  return `<div class="studio-baris">
      <div class="studio-judul"><span class="label merah">${esc(j0.tipe)}</span> ${esc(j0.studioNama)}
        <b class="kanan">${rupiah(j0.harga)}${j0.akhirPekan ? ' <small class="redup">(akhir pekan)</small>' : ''}</b></div>
      <div class="redup kecil">${esc(j0.deskripsiStudio)} • beli ≥5 tiket hemat ${j0.persenDiskon}%</div>
      <div class="daftar-jam">${jam}</div></div>`;
}
function htmlKelompok(judul, list) {
  return `<div class="kartu"><h3>${esc(judul)}</h3>${[...kelompok(list, j => j.studioId).values()].map(htmlStudio).join('')}</div>`;
}
function htmlTanggal(daftar, dipilih) {
  return `<div class="chips">${daftar.map((t, i) => `<button class="chip ${t === dipilih ? 'aktif' : ''}" data-tgl="${t}">
    <b>${labelHari(t, i)}</b><small>${tglPendek(t)}</small></button>`).join('')}</div>`;
}

/** Menampilkan pilihan tanggal (maks. 3 hari) lalu memuat jadwal sesuai filter. */
async function pasangJadwal(tanggal, query, kelompokkan) {
  if (!tanggal.includes(S.tanggal)) S.tanggal = tanggal[0];
  const muat = async () => {
    $('#tgl').innerHTML = htmlTanggal(tanggal, S.tanggal);
    $$('[data-tgl]').forEach(b => { b.onclick = () => { S.tanggal = b.dataset.tgl; muat(); }; });
    $('#daftar').innerHTML = '<p class="muat">Memuat jadwal…</p>';
    try {
      const list = await api(`/api/jadwal?tanggal=${S.tanggal}&${query}`);
      $('#daftar').innerHTML = list.length
        ? `<div class="daftar">${[...kelompok(list, kelompokkan)].map(([judul, l]) => htmlKelompok(judul, l)).join('')}</div>`
        : '<p class="kosong">Tidak ada jadwal tersisa pada tanggal ini. Coba tanggal atau kota lain.</p>';
    } catch (err) {
      $('#daftar').innerHTML = `<p class="kosong">${esc(err.message)}</p>`;
    }
  };
  await muat();
}

async function halJadwal(filmId) {
  const [film, tanggal] = await Promise.all([api('/api/film/' + filmId), api('/api/jadwal/tanggal')]);
  const kota = S.kota.find(k => k.id === S.kotaId);
  tampil(`<a class="kembali" href="#/film/${filmId}">← ${esc(film.judul)}</a><h1>Pilih Jadwal</h1>
    <p class="redup">Kota: <b>${esc(kota ? kota.nama : '-')}</b> (ganti di pojok kanan atas) • jadwal tersedia maksimal 3 hari</p>
    <div id="tgl"></div><div id="daftar"></div>`);
  await pasangJadwal(tanggal, `filmId=${filmId}&kotaId=${S.kotaId}`, j => j.cinemaNama);
}

// =====================================================================
// Halaman: bioskop
// =====================================================================
async function halBioskop() {
  const list = await api('/api/cinema?kotaId=' + S.kotaId);
  tampil('<h1>Bioskop</h1>' + (list.length
    ? `<div class="daftar">${list.map(c => `<a class="kartu tautan" href="#/bioskop/${c.id}"><h3>${esc(c.nama)}</h3>
        <p class="redup">${esc(c.alamat)}</p><p>${(c.tipeStudio || []).map(t => `<span class="label">${esc(t)}</span>`).join(' ')}</p></a>`).join('')}</div>`
    : '<p class="kosong">Belum ada bioskop di kota ini.</p>'));
}

async function halBioskopDetail(id) {
  const [c, tanggal] = await Promise.all([api('/api/cinema/' + id), api('/api/jadwal/tanggal')]);
  tampil(`<a class="kembali" href="#/bioskop">← Bioskop</a><h1>${esc(c.nama)}</h1><p class="redup">${esc(c.alamat)}</p>
    <div id="tgl"></div><div id="daftar"></div>`);
  await pasangJadwal(tanggal, `cinemaId=${id}`, j => j.filmJudul);
}

// =====================================================================
// Halaman: pilih kursi (denah dari tabel `kursi`)
// =====================================================================
const IKON_LAYAR = '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 20h8M12 16v4"/></svg>';

async function halKursi(jadwalId) {
  const d = await api(`/api/jadwal/${jadwalId}/kursi`);
  const j = d.jadwal;
  if (S.kursi.jadwalId !== Number(jadwalId)) S.kursi = { jadwalId: Number(jadwalId), dipilih: [] };
  const semua = d.section.flatMap(s => s.baris.flatMap(b => b.kiri.concat(b.kanan)));
  const terisi = new Set(semua.filter(k => k.terisi).map(k => k.kode));
  S.kursi.dipilih = S.kursi.dipilih.filter(k => !terisi.has(k));

  const lihatSaja = !!(S.pengguna && S.pengguna.peran === 'ADMIN');   // admin hanya melihat denah, tidak bisa memesan
  const perSisi = d.kursiPerBaris / 2;
  const tombol = k => `<button class="kursi${k.terisi ? ' terisi' : ''}" data-kode="${esc(k.kode)}" ${k.terisi || lihatSaja ? 'disabled' : ''} title="${esc(k.kode)}">${k.nomor}</button>`;
  const blok = list => list.map(tombol).join('') + '<span class="kursi kosong-slot"></span>'.repeat(Math.max(0, perSisi - list.length));

  tampil(`<a class="kembali" href="#/jadwal/${j.filmId}">← Ganti jadwal</a>
    <div class="kursi-layout">
      <div class="kartu denah">
        <div class="layar">${IKON_LAYAR}<b>LAYAR BIOSKOP</b><small>DEPAN STUDIO</small></div>
        ${d.section.map(s => `<div class="section"><div class="section-judul">${esc(s.label)}</div>
          ${s.baris.map(b => `<div class="baris"><span class="lbl">${esc(b.baris)}</span><div class="blok">${blok(b.kiri)}</div>
            <div class="lorong" title="Jalan tengah"></div><div class="blok">${blok(b.kanan)}</div><span class="lbl">${esc(b.baris)}</span></div>`).join('')}
        </div>`).join('')}
        <div class="legenda"><span><i class="kotak"></i>Tersedia</span><span><i class="kotak dipilih"></i>Dipilih</span><span><i class="kotak terisi"></i>Terisi</span></div>
      </div>
      <aside class="kartu ringkas">
        <h3>${esc(j.filmJudul)}</h3>
        <p class="redup kecil">${esc(j.cinemaNama)}<br>${esc(j.studioNama)} (${esc(j.tipe)}) • ${tglPanjang(j.tanggal)} • ${esc(j.jam)}</p>
        <div id="ringkas"></div>
        ${lihatSaja ? '' : '<button class="btn penuh" id="lanjut">Lanjut Bayar</button>'}
      </aside>
    </div>`);

  const perbarui = () => {
    if (lihatSaja) {
      const total = semua.length, kosong = total - terisi.size;
      $('#ringkas').innerHTML = `
        <div class="baris-nilai"><span>Kursi tersedia</span><b>${kosong}</b></div>
        <div class="baris-nilai"><span>Kursi terisi</span><b>${terisi.size}</b></div>
        <div class="baris-nilai"><span>Total kursi</span><b>${total}</b></div>
        <p class="redup kecil">Mode lihat saja untuk admin. Denah ini hanya informasi, kursi tidak bisa dipesan.</p>`;
      return;
    }
    $$('.kursi[data-kode]').forEach(b => b.classList.toggle('dipilih', S.kursi.dipilih.includes(b.dataset.kode)));
    const n = S.kursi.dipilih.length;
    const subtotal = j.harga * n;
    const diskon = n >= d.minTiketDiskon ? Math.round(subtotal * d.persenDiskon / 100) : 0;
    $('#ringkas').innerHTML = `
      <div class="baris-nilai"><span>Kursi</span><b>${n ? esc(urutKursi(S.kursi.dipilih).join(', ')) : '-'}</b></div>
      <div class="baris-nilai"><span>${n} x ${rupiah(j.harga)}</span><b>${rupiah(subtotal)}</b></div>
      ${diskon
        ? `<div class="baris-nilai hijau"><span>Diskon ${d.persenDiskon}% (≥${d.minTiketDiskon} tiket)</span><b>-${rupiah(diskon)}</b></div>`
        : `<p class="redup kecil">Beli ${d.minTiketDiskon} tiket atau lebih, hemat ${d.persenDiskon}%. Maksimal ${d.maksPilih} kursi.</p>`}
      <div class="baris-nilai total"><span>Total</span><b>${rupiah(subtotal - diskon)}</b></div>`;
    $('#lanjut').disabled = n === 0;
  };

  $$('.kursi[data-kode]').forEach(b => {
    b.onclick = () => {
      const kode = b.dataset.kode;
      if (S.kursi.dipilih.includes(kode)) S.kursi.dipilih = S.kursi.dipilih.filter(k => k !== kode);
      else if (S.kursi.dipilih.length >= d.maksPilih) toast(`Maksimal ${d.maksPilih} kursi per transaksi.`, 'gagal');
      else S.kursi.dipilih.push(kode);
      perbarui();
    };
  });

  if (!lihatSaja) $('#lanjut').onclick = async () => {
    const btn = $('#lanjut');
    btn.disabled = true; btn.textContent = 'Memproses…';
    try {
      const b = await api('/api/booking', { method: 'POST', body: { jadwalId: Number(jadwalId), kursi: S.kursi.dipilih } });
      S.kursi = { jadwalId: null, dipilih: [] };
      location.hash = '#/bayar/' + b.id;
    } catch (err) {
      toast(err.message, 'gagal');
      await halKursi(jadwalId);          // tampilkan denah terbaru (misal kursi baru diambil orang lain)
    }
  };
  perbarui();
}

// =====================================================================
// Halaman: pembayaran (simulasi)
// =====================================================================
function htmlRingkasan(b) {
  const j = b.jadwal;
  return `<div class="kartu"><h3>${esc(j.filmJudul)}</h3>
    <p class="redup kecil">${esc(j.cinemaNama)} • ${esc(j.studioNama)} (${esc(j.studioTipe)})<br>${tglPanjang(j.tanggal)} • ${esc(j.jam)}</p>
    <div class="baris-nilai"><span>${b.kursi.length} tiket (${esc(b.kursi.join(', '))})</span><b>${rupiah(b.subtotalTiket)}</b></div>
    ${b.diskon > 0 ? `<div class="baris-nilai hijau"><span>Diskon beli ≥5 tiket</span><b>-${rupiah(b.diskon)}</b></div>` : ''}
    ${b.bonus ? `<div class="baris-nilai"><span>Bonus studio</span><b>${esc(b.bonus)}</b></div>` : ''}
    <div class="baris-nilai total"><span>Subtotal</span><b>${rupiah(b.subtotalTiket - b.diskon)}</b></div></div>`;
}

async function halBayar(id) {
  const b = await api('/api/booking/' + id);
  if (b.status === 'LUNAS') { location.hash = '#/tiket/' + id; return; }
  if (b.status === 'BATAL') {
    tampil('<div class="kosong"><h2>Pesanan dibatalkan</h2><p>Waktu pembayaran habis atau pesanan dibatalkan.</p><a class="btn" href="#/film">Pesan lagi</a></div>');
    return;
  }
  let sisa = b.sisaDetikBayar;
  tampil(`<h1>Pembayaran</h1><div class="dua-kolom"><div>${htmlRingkasan(b)}</div>
    <div class="kartu"><div class="timer" id="timer"></div><div id="area-bayar"></div>
    <button class="btn teks penuh" id="batal">Batalkan pesanan</button></div></div>`);
  const tick = () => {
    $('#timer').textContent = sisa > 0 ? `Selesaikan pembayaran dalam ${mmss(sisa)}` : 'Waktu pembayaran habis. Pesanan akan dibatalkan otomatis.';
    if (sisa > 0) sisa--;
  };
  tick();
  S.timer = setInterval(tick, 1000);
  formMetode(b);
  $('#batal').onclick = async () => {
    if (!confirm('Batalkan pesanan? Kursi akan dilepas.')) return;
    try {
      await api(`/api/booking/${id}/batal`, { method: 'POST' });
      toast('Pesanan dibatalkan.');
      location.hash = '#/riwayat';
    } catch (err) { toast(err.message, 'gagal'); }
  };
}

function formMetode(b) {
  $('#area-bayar').innerHTML = `<h3>Metode pembayaran</h3><form id="f-bayar">
    <label class="opsi"><input type="radio" name="metode" value="QRIS" checked><span><b>QRIS</b><small class="redup">Tanpa biaya admin</small></span></label>
    <label class="opsi"><input type="radio" name="metode" value="BANK"><span><b>Transfer Bank (Virtual Account)</b><small class="redup">Biaya admin Rp2.500</small></span></label>
    <div class="sub" data-untuk="BANK" hidden><select name="bank">${['BCA', 'BNI', 'BRI', 'MANDIRI'].map(x => `<option>${x}</option>`).join('')}</select></div>
    <label class="opsi"><input type="radio" name="metode" value="KREDIT"><span><b>Kartu Kredit</b><small class="redup">Biaya admin 2%, langsung lunas</small></span></label>
    <div class="sub" data-untuk="KREDIT" hidden><input name="noKartu" inputmode="numeric" maxlength="19" placeholder="4111 1111 1111 1111">
      <small class="redup">Simulasi: kartu uji 4111 1111 1111 1111</small></div>
    <button class="btn penuh" type="submit">Bayar Sekarang</button></form>`;
  const f = $('#f-bayar');
  const sinkron = () => { $$('.sub', f).forEach(s => { s.hidden = s.dataset.untuk !== nilai(f, 'metode'); }); };
  $$('input[name=metode]', f).forEach(r => { r.onchange = sinkron; });
  f.onsubmit = async e => {
    e.preventDefault();
    const metode = nilai(f, 'metode');
    const body = { metode, bank: metode === 'BANK' ? nilai(f, 'bank') : null, noKartu: metode === 'KREDIT' ? nilai(f, 'noKartu').replace(/\D/g, '') : null };
    const btn = $('button[type=submit]', f);
    btn.disabled = true; btn.textContent = 'Memproses…';
    try {
      const ins = await api(`/api/booking/${b.id}/bayar`, { method: 'POST', body });
      if (ins.status === 'LUNAS') { toast('Pembayaran berhasil!', 'sukses'); location.hash = '#/tiket/' + b.id; } else tampilInstruksi(b, ins);
    } catch (err) {
      toast(err.message, 'gagal');
      btn.disabled = false; btn.textContent = 'Bayar Sekarang';
    }
  };
}

function tampilInstruksi(b, i) {
  $('#area-bayar').innerHTML = `<h3>${esc(i.metode)}</h3>
    <div class="baris-nilai"><span>Subtotal</span><b>${rupiah(i.subtotal)}</b></div>
    ${i.diskon > 0 ? `<div class="baris-nilai hijau"><span>Diskon</span><b>-${rupiah(i.diskon)}</b></div>` : ''}
    <div class="baris-nilai"><span>Biaya admin</span><b>${rupiah(i.biayaAdmin)}</b></div>
    <div class="baris-nilai total"><span>Total bayar</span><b>${rupiah(i.totalBayar)}</b></div>
    ${i.qrisPayload ? `<div class="qr"><img alt="QRIS" src="/api/qr?isi=${encodeURIComponent(i.qrisPayload)}"><small class="redup">${esc(i.qrisPayload)}</small></div>` : ''}
    ${i.nomorVa ? `<div class="va"><small class="redup">Nomor Virtual Account</small><b>${esc(i.nomorVa.replace(/(.{4})/g, '$1 ').trim())}</b></div>` : ''}
    <p class="redup kecil">${esc(i.instruksi)}</p>
    <button class="btn penuh" id="sudah">Saya Sudah Bayar (Simulasi)</button>
    <button class="btn garis penuh" id="ganti">Ganti metode pembayaran</button>`;
  $('#sudah').onclick = async e => {
    e.target.disabled = true;
    try {
      await api(`/api/booking/${b.id}/konfirmasi`, { method: 'POST' });
      toast('Pembayaran berhasil!', 'sukses');
      location.hash = '#/tiket/' + b.id;
    } catch (err) { toast(err.message, 'gagal'); e.target.disabled = false; }
  };
  $('#ganti').onclick = () => formMetode(b);
}

// =====================================================================
// Halaman: e-ticket & riwayat
// =====================================================================
const NAMA_STATUS = { LUNAS: 'Lunas', PENDING: 'Menunggu bayar', BATAL: 'Dibatalkan' };
const labelStatus = s => `<span class="status ${s.toLowerCase()}">${NAMA_STATUS[s] || esc(s)}</span>`;

async function halTiket(id) {
  const b = await api('/api/booking/' + id);
  if (b.status !== 'LUNAS') {
    tampil(`<a class="kembali" href="#/riwayat">← Riwayat</a><h1>Booking #${b.id} ${labelStatus(b.status)}</h1>${htmlRingkasan(b)}
      ${b.status === 'PENDING'
        ? `<p>Pesanan ini belum dibayar. Kursi ditahan maksimal 15 menit sejak dipesan.</p>
           <a class="btn" href="#/bayar/${b.id}">Lanjutkan Pembayaran</a> <button class="btn garis" id="batal">Batalkan</button>`
        : '<p class="redup">Pesanan ini dibatalkan (tidak dibayar tepat waktu atau dibatalkan pelanggan).</p>'}`);
    if (b.status === 'PENDING') {
      $('#batal').onclick = async () => {
        try { await api(`/api/booking/${b.id}/batal`, { method: 'POST' }); jalankan(); } catch (err) { toast(err.message, 'gagal'); }
      };
    }
    return;
  }
  const j = b.jadwal;
  tampil(`<a class="kembali tanpa-cetak" href="#/riwayat">← Riwayat</a>
    <div class="tiket" id="tiket">
      <div class="tiket-atas"><span class="logo">CINETIX</span><span>E-TICKET</span></div>
      <h2>${esc(j.filmJudul)} <small>(${esc(j.filmRating || 'SU')})</small></h2>
      <div class="tiket-grid">
        <div><small>Bioskop</small><b>${esc(j.cinemaNama)}</b></div>
        <div><small>Studio</small><b>${esc(j.studioNama)} (${esc(j.studioTipe)})</b></div>
        <div><small>Tanggal</small><b>${tglPanjang(j.tanggal)}</b></div>
        <div><small>Jam</small><b>${esc(j.jam)}</b></div>
        <div><small>Kursi</small><b>${esc(b.kursi.join(', '))}</b></div>
        <div><small>Pemesan</small><b>${esc(b.namaPengguna)}</b></div>
        ${b.bonus ? `<div><small>Bonus</small><b>${esc(b.bonus)}</b></div>` : ''}
        ${b.diskon > 0 ? `<div><small>Diskon</small><b>${rupiah(b.diskon)}</b></div>` : ''}
        <div><small>Total bayar</small><b>${rupiah(b.total)} via ${esc(b.metodeBayar || '-')}</b></div>
        <div><small>Dibayar</small><b>${waktu(b.dibayar)}</b></div>
      </div>
      <div class="barcode"><img alt="Barcode ${esc(b.kodeTiket)}" src="/api/barcode/${encodeURIComponent(b.kodeTiket)}">
        <code>${esc(b.kodeTiket)}</code><small>Tunjukkan barcode ini di pintu studio</small></div>
    </div>
    <button class="btn tanpa-cetak" id="cetak">Cetak / Simpan PDF</button>`);
  $('#cetak').onclick = () => window.print();
}

async function halRiwayat() {
  const list = await api('/api/booking');
  tampil('<h1>Riwayat Pembelian</h1>' + (list.length
    ? `<div class="daftar">${list.map(b => `<a class="kartu tautan" href="#/tiket/${b.id}">
        <div class="baris-nilai"><b>${esc(b.jadwal.filmJudul)}</b>${labelStatus(b.status)}</div>
        <div class="redup kecil">${esc(b.jadwal.cinemaNama)} • ${esc(b.jadwal.studioTipe)} • ${tglPanjang(b.jadwal.tanggal)} • ${esc(b.jadwal.jam)}</div>
        <div class="baris-nilai"><span class="redup kecil">Kursi ${esc(b.kursi.join(', '))} • dipesan ${waktu(b.dibuat)}</span><b>${rupiah(b.total)}</b></div></a>`).join('')}</div>`
    : '<p class="kosong">Belum ada pembelian. Yuk pesan tiket pertamamu!</p>'));
}

async function halProfil() {
  const p = await api('/api/me');
  simpanSesi(S.token, p);
  tampil(`<div class="kartu profil"><div class="avatar">${esc(p.nama.charAt(0).toUpperCase())}</div>
    <h2>${esc(p.nama)}</h2><p class="redup">${esc(p.email)}</p><span class="label">${p.peran === 'ADMIN' ? 'Admin' : 'Pelanggan'}</span>
    <button class="btn merah penuh" id="keluar">Keluar</button></div>`);
  $('#keluar').onclick = async () => {
    try { await api('/api/auth/logout', { method: 'POST' }); } catch (e) { /* tetap keluar */ }
    simpanSesi(null, null);
    S.kota = [];
    location.hash = '#/login';
  };
}

// =====================================================================
// Halaman admin: laporan, CRUD generik, harga studio
// =====================================================================
const ENTITAS = {
  film: {
    judul: 'Film',
    fields: [['judul', 'Judul', 'teks'], ['genre', 'Genre', 'teks'], ['durasi', 'Durasi (menit)', 'angka'],
      ['ratingUsia', 'Rating usia', 'pilihan', ['SU', 'R13', 'D17', 'D21']],
      ['status', 'Status', 'pilihan', ['NOW_PLAYING', 'COMING_SOON']],
      ['tanggalRilis', 'Tanggal rilis (opsional)', 'tanggal'], ['sinopsis', 'Sinopsis', 'panjang'],
      ['posterUrl', 'Poster film (opsional)', 'gambar']],
    item: x => [x.judul, `${x.genre} • ${x.durasi} mnt • ${x.ratingUsia} • ${x.status}`]
  },
  jadwal: {
    judul: 'Jadwal',
    fields: [['filmId', 'Film', 'relasi', 'film'], ['studioId', 'Studio', 'relasi', 'studio'],
      ['tanggal', 'Tanggal', 'tanggal'], ['jam', 'Jam', 'jam']],
    item: x => [x.filmJudul, `${x.tanggal} ${x.jam} • ${x.cinemaNama} • ${x.studioNama} (${x.studioTipe})`]
  },
  cinema: {
    judul: 'Cinema',
    fields: [['nama', 'Nama cinema', 'teks'], ['alamat', 'Alamat', 'panjang'], ['kotaId', 'Kota', 'relasi', 'kota']],
    item: x => [x.nama, `${x.kotaNama} • ${x.alamat}`]
  },
  studio: {
    judul: 'Studio',
    fields: [['cinemaId', 'Cinema', 'relasi', 'cinema'], ['nama', 'Nama studio (contoh: Studio 6)', 'teks'],
      ['tipe', 'Tipe', 'pilihan', ['REGULER', 'PREMIERE', 'SUITES']],
      ['kapasitas', 'Kapasitas (kelipatan: Reguler 10, Premiere 8, Suites 6)', 'angka']],
    item: x => [`${x.cinemaNama} • ${x.nama}`, `${x.tipe} • ${x.kapasitas} kursi (denah dibuat otomatis)`]
  },
  kota: { judul: 'Kota', fields: [['nama', 'Nama kota', 'teks']], item: x => [x.nama, 'ID ' + x.id] }
};
const LABEL_RELASI = {
  film: x => x.judul, cinema: x => x.nama, kota: x => x.nama,
  studio: x => `${x.cinemaNama} - ${x.nama} (${x.tipe})`
};
const BAGIAN_ADMIN = [['laporan', 'Laporan'], ['film', 'Film'], ['jadwal', 'Jadwal'], ['cinema', 'Cinema'],
  ['studio', 'Studio'], ['kota', 'Kota'], ['harga', 'Harga Studio']];

async function halAdmin(bagian) {
  if (!BAGIAN_ADMIN.some(b => b[0] === bagian)) { location.hash = '#/admin/laporan'; return; }
  const tab = `<h1>Admin Cinetix</h1><div class="tabs gulir">${BAGIAN_ADMIN.map(([k, l]) =>
    `<a href="#/admin/${k}" class="${k === bagian ? 'aktif' : ''}">${l}</a>`).join('')}</div>`;
  if (bagian === 'laporan') return adminLaporan(tab);
  if (bagian === 'harga') return adminHarga(tab);
  return adminCrud(tab, bagian);
}

async function adminLaporan(tab) {
  const l = await api('/api/admin/laporan');
  const maks = Math.max(1, ...l.filmTerlaris.map(f => f.tiket));
  const stat = [['Total pendapatan', rupiah(l.totalPendapatan)], ['Transaksi lunas', l.jumlahTransaksi],
    ['Tiket terjual', l.tiketTerjual], ['Total diskon diberikan', rupiah(l.totalDiskon)]];
  tampil(tab + `<div class="stat">${stat.map(([a, b]) => `<div class="kartu"><small class="redup">${a}</small><b>${b}</b></div>`).join('')}</div>
    <div class="dua-kolom">
      <div class="kartu"><h3>Film terlaris</h3>${l.filmTerlaris.length ? l.filmTerlaris.map((f, i) => `
        <div class="baris-nilai"><span>${i + 1}. ${esc(f.judul)}</span><b>${f.tiket} tiket • ${rupiah(f.pendapatan)}</b></div>
        <div class="bar"><i style="width:${(f.tiket / maks) * 100}%"></i></div>`).join('') : '<p class="redup">Belum ada penjualan.</p>'}</div>
      <div class="kartu"><h3>Per metode pembayaran</h3>${l.perMetode.map(m =>
        `<div class="baris-nilai"><span>${esc(m.metode)} (${m.transaksi}x)</span><b>${rupiah(m.total)}</b></div>`).join('') || '<p class="redup">-</p>'}</div>
    </div>`);
}

async function adminHarga(tab) {
  const list = await api('/api/admin/harga-studio');
  tampil(tab + `<p class="redup">Harga akhir pekan otomatis +Rp15.000. Diskon berlaku jika membeli minimal 5 tiket.</p>
    <div class="kartu tabel-wrap"><table><thead><tr><th>Tipe</th><th>Hari kerja</th><th>Akhir pekan</th><th>Diskon ≥5 tiket</th><th>Kursi/baris</th><th></th></tr></thead>
    <tbody>${list.map(h => `<tr><td>${esc(h.label)}</td><td>${rupiah(h.hargaDasar)}</td><td>${rupiah(h.hargaAkhirPekan)}</td>
      <td>${h.persenDiskon}%</td><td>${h.kursiPerBaris}</td>
      <td><button class="btn kecil garis" data-tipe="${esc(h.tipe)}" data-harga="${h.hargaDasar}">Ubah</button></td></tr>`).join('')}</tbody></table></div>`);
  $$('[data-tipe]').forEach(b => {
    b.onclick = () => bukaModal(`Harga ${b.dataset.tipe}`,
      `<label>Harga dasar (hari kerja)<input name="harga" type="number" min="10000" step="1000" value="${Math.round(b.dataset.harga)}" required></label>`,
      async f => {
        await api('/api/admin/harga-studio/' + encodeURIComponent(b.dataset.tipe), { method: 'PUT', body: { harga: Number(nilai(f, 'harga')) } });
        toast('Harga diperbarui.', 'sukses');
        jalankan();
      });
  });
}

function inputField([k, label, jenis, ekstra], awal, opsiRelasi) {
  const v = awal && awal[k] != null ? String(awal[k]) : '';
  let el;
  if (jenis === 'gambar') {
    return `<div class="input-gambar"><span>${esc(label)}</span>
      <img id="pratinjau-poster" class="pratinjau" alt="Pratinjau poster" ${v ? `src="${esc(v)}"` : 'hidden'}>
      <input name="poster_file" type="file" accept="image/jpeg,image/png,image/webp">
      <small class="redup">JPG/PNG/WEBP. Otomatis diperkecil sebelum diunggah. Biarkan kosong jika tidak ingin mengubah poster.</small>
      ${v ? '<label class="centang"><input name="hapus_poster" type="checkbox"> Hapus poster saat ini</label>' : ''}</div>`;
  }
  if (jenis === 'panjang') el = `<textarea name="${k}" rows="3">${esc(v)}</textarea>`;
  else if (jenis === 'pilihan') el = `<select name="${k}">${ekstra.map(o => `<option value="${esc(o)}" ${o === v ? 'selected' : ''}>${esc(({ NOW_PLAYING: 'Sedang Tayang', COMING_SOON: 'Segera Tayang' })[o] || o)}</option>`).join('')}</select>`;
  else if (jenis === 'relasi') {
    el = `<select name="${k}" required><option value="">Pilih…</option>${(opsiRelasi[ekstra] || []).map(o =>
      `<option value="${o.id}" ${String(o.id) === v ? 'selected' : ''}>${esc(LABEL_RELASI[ekstra](o))}</option>`).join('')}</select>`;
  } else {
    const tipe = { angka: 'number', tanggal: 'date', jam: 'time' }[jenis] || 'text';
    el = `<input name="${k}" type="${tipe}" value="${esc(v)}">`;
  }
  return `<label>${esc(label)}${el}</label>`;
}

async function adminCrud(tab, kunci) {
  const e = ENTITAS[kunci];
  const relasi = [...new Set(e.fields.filter(f => f[2] === 'relasi').map(f => f[3]))];
  const [data, ...opsi] = await Promise.all([api('/api/admin/' + kunci), ...relasi.map(r => api('/api/admin/' + r))]);
  const opsiRelasi = Object.fromEntries(relasi.map((r, i) => [r, opsi[i]]));

  tampil(tab + `<div class="aksi-atas"><button class="btn" id="tambah">+ Tambah ${esc(e.judul)}</button>
      ${kunci === 'jadwal' ? '<button class="btn" id="generate">Generate 3 hari</button>' : ''}</div>
    ${data.length ? `<div class="daftar">${data.map(x => {
      const [a, b] = e.item(x);
      return `<div class="kartu item-admin"><div><b>${esc(a)}</b><small class="redup">${esc(b)}</small></div>
        <div class="aksi"><button class="btn kecil garis" data-ubah="${x.id}">Ubah</button><button class="btn kecil merah" data-hapus="${x.id}">Hapus</button></div></div>`;
    }).join('')}</div>` : `<p class="kosong">Belum ada data ${esc(e.judul)}.</p>`}`);

  const form = awal => {
    bukaModal((awal ? 'Ubah ' : 'Tambah ') + e.judul, e.fields.map(f => inputField(f, awal, opsiRelasi)).join(''), async fm => {
    const body = {};
    e.fields.forEach(([k, , jenis]) => {
      if (jenis === 'gambar') return;               // poster dikirim terpisah (bukan bagian JSON)
      const v = nilai(fm, k).trim();
      if (v !== '') body[k] = ['angka', 'relasi'].includes(jenis) ? Number(v) : v;
    });
    const h = await api('/api/admin/' + kunci + (awal ? '/' + awal.id : ''), { method: awal ? 'PUT' : 'POST', body });
    let pesan = 'Data tersimpan.', jenisToast = 'sukses';
    const idData = h.id || (awal && awal.id);
    const file = kunci === 'film' ? $('[name="poster_file"]', fm).files[0] : null;
    const hapusPoster = kunci === 'film' && awal && $('[name="hapus_poster"]', fm)?.checked;
    // Data sudah tersimpan; kegagalan poster tidak boleh membuat form dikirim ulang (bisa menggandakan film)
    try {
      if (file && idData) {
        await apiUnggah(`/api/admin/film/${idData}/poster`, await kecilkanGambar(file));
        pesan = 'Data film dan poster tersimpan.';
      } else if (hapusPoster) {
        await api(`/api/admin/film/${awal.id}/poster`, { method: 'DELETE' });
        pesan = 'Data tersimpan, poster dihapus.';
      }
    } catch (err) {
      pesan = 'Data tersimpan, tetapi poster gagal diproses: ' + err.message;
      jenisToast = 'gagal';
    }
    toast(pesan, jenisToast);
    jalankan();
  });
    // Pratinjau poster langsung saat admin memilih file (formulir baru ada setelah modal dibuka)
    const inputPoster = $('[name="poster_file"]', $('#modal'));
    if (inputPoster) {
      inputPoster.onchange = () => {
        const f = inputPoster.files[0], img = $('#pratinjau-poster');
        if (f && img) { img.src = URL.createObjectURL(f); img.hidden = false; }
      };
    }
  };
  $('#tambah').onclick = () => form(null);
  $$('[data-ubah]').forEach(b => { b.onclick = () => form(data.find(x => x.id === Number(b.dataset.ubah))); });
  $$('[data-hapus]').forEach(b => {
    b.onclick = async () => {
      const x = data.find(y => y.id === Number(b.dataset.hapus));
      if (!confirm(`Hapus "${e.item(x)[0]}"?`)) return;
      try { await api(`/api/admin/${kunci}/${x.id}`, { method: 'DELETE' }); toast('Data dihapus.', 'sukses'); jalankan(); } catch (err) { toast(err.message, 'gagal'); }
    };
  });
  if (kunci === 'jadwal') {
    $('#generate').onclick = async () => {
      try { const h = await api('/api/admin/jadwal/generate', { method: 'POST' }); toast(h.pesan, 'sukses'); jalankan(); } catch (err) { toast(err.message, 'gagal'); }
    };
  }
}

// =====================================================================
window.addEventListener('hashchange', jalankan);
// Saat halaman dibuka: kalau ada sesi di tab ini, pastikan tokennya masih berlaku di server.
// Jika tidak (misalnya backend di-restart), api() otomatis membersihkan sesi dan kembali ke login.
async function mulai() {
  if (S.token) {
    try { simpanSesi(S.token, await api('/api/me')); } catch (e) { /* sesi tidak berlaku -> login */ }
  }
  jalankan();
}
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mulai); else mulai();