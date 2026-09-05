import { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import {
  Building2,
  Clock,
  Images,
  Inbox,
  Menu,
  Scissors,
  SquareCheckBig,
  Users,
  X,
} from "lucide-react";
import api from "../../api/axios";
import { resolvePhotoUrl } from "../../utils/photo";
import InboxTab from "./InboxTab";
import ApprovedTab from "./ApprovedTab";
import ServicesTab from "./ServicesTab";
import StaffTab from "./StaffTab";
import WorkingHoursTab from "./WorkingHoursTab";
import LocationInfoTab from "./LocationInfoTab";
import GalleryTab from "./GalleryTab";
import type { BusinessResponse } from "../../types/api";

type TabKey = "inbox" | "approved" | "services" | "staff" | "hours" | "location" | "gallery";

// Rozet renkleri sekmeye gore SABIT (mockup'taki gibi): Istek Kutusu
// turuncu/amber, Onaylananlar yesil -- ikisi de "dikkat gerektiren" sayilar.
// Hizmetler/Personel duz gri metin -- bunlar bir eylem beklemiyor, sadece
// bilgi. Calisma Saatleri/Konum/Galeri'de rozet yok (sayilabilir bir "bekleyen"
// kavramlari yok).
const TABS: { key: TabKey; label: string; icon: typeof Inbox; badgeStyle?: "amber" | "green" }[] = [
  { key: "inbox", label: "İstek Kutusu", icon: Inbox, badgeStyle: "amber" },
  { key: "approved", label: "Onaylananlar", icon: SquareCheckBig, badgeStyle: "green" },
  { key: "services", label: "Hizmetler", icon: Scissors },
  { key: "staff", label: "Personel", icon: Users },
  { key: "hours", label: "Çalışma Saatleri", icon: Clock },
  { key: "location", label: "Konum & İletişim", icon: Building2 },
  { key: "gallery", label: "Galeri & Fotoğraflar", icon: Images },
];

export default function BusinessPanelPage() {
  const [businesses, setBusinesses] = useState<BusinessResponse[]>([]);
  const [selectedBusinessId, setSelectedBusinessId] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<TabKey>("inbox");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // Mobilde sidebar varsayilan kapali -- masaustunde (lg:) CSS zaten hep
  // acik gosteriyor, bu state sadece mobil gorunumde etkili.
  const [sidebarOpen, setSidebarOpen] = useState(false);
  // Sekme rozetleri -- her sekme kendi gercek verisini cektiginde
  // onCountChange ile buraya bildiriyor (bkz. InboxTab/ApprovedTab/
  // ServicesTab/StaffTab). Sahte/sabit sayi YOK.
  const [counts, setCounts] = useState<Partial<Record<TabKey, number>>>({});

  useEffect(() => {
    fetchBusinesses();
  }, []);

  // Isletme degisince rozet sayilari eski isletmeye ait kalmasin.
  useEffect(() => {
    setCounts({});
  }, [selectedBusinessId]);

  const makeCountHandler = useCallback(
    (key: TabKey) => (count: number) => setCounts((prev) => (prev[key] === count ? prev : { ...prev, [key]: count })),
    [],
  );

  async function fetchBusinesses() {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<BusinessResponse[]>("/api/businesses/my");
      setBusinesses(res.data);
      if (res.data.length > 0) {
        setSelectedBusinessId(res.data[0].id);
      }
    } catch {
      setError("İşletmeleriniz yüklenirken hata oluştu.");
    } finally {
      setLoading(false);
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center py-20">
        <div className="flex items-center gap-3 text-slate-400">
          <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          Yükleniyor...
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-16">
        <p className="text-5xl mb-4">⚠️</p>
        <p className="text-red-500 text-lg mb-4">{error}</p>
        <button
          onClick={fetchBusinesses}
          className="text-brand hover:text-brand-hover text-sm font-medium cursor-pointer"
        >
          Tekrar Dene
        </button>
      </div>
    );
  }

  if (businesses.length === 0) {
    return (
      <div className="text-center py-20 max-w-lg mx-auto px-4">
        <div className="w-20 h-20 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-5">
          <Building2 className="w-9 h-9 text-slate-400" />
        </div>
        <p className="text-slate-900 text-lg font-medium mb-1">Henüz bir işletmeniz yok</p>
        <p className="text-slate-500 text-sm">
          İşletme oluşturma ekranı yakında eklenecek. Şimdilik veritabanı üzerinden bir işletme
          sahibi olmanız gerekiyor.
        </p>
      </div>
    );
  }

  const selectedBusiness = businesses.find((b) => b.id === selectedBusinessId);
  const coverUrl = selectedBusiness ? resolvePhotoUrl(selectedBusiness.coverPhotoCardUrl) : null;

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8">
      {/* Mobilde sidebar acma dugmesi -- lg ve ustunde sidebar zaten hep
          acik/sabit oldugu icin gizli. */}
      <button
        type="button"
        onClick={() => setSidebarOpen(true)}
        className="lg:hidden flex items-center gap-2 mb-4 px-3 py-2 bg-white border border-slate-200 rounded-xl text-sm font-medium text-slate-700 shadow-sm cursor-pointer"
      >
        <Menu className="w-4 h-4" />
        Menü
      </button>

      <div className="flex gap-6 items-start">
        {/* Mobilde sidebar acikken arkaya karartma -- tiklayinca kapanir. */}
        {sidebarOpen && (
          <div
            className="fixed inset-0 bg-black/40 z-40 lg:hidden"
            onClick={() => setSidebarOpen(false)}
          />
        )}

        {/* Sidebar */}
        <aside
          className={`fixed lg:static inset-y-0 left-0 z-50 w-72 lg:w-64 shrink-0 bg-white border-r lg:border border-slate-200 lg:rounded-2xl lg:shadow-sm overflow-y-auto transition-transform duration-200 lg:transition-none ${
            sidebarOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"
          }`}
        >
          <div className="p-5 border-b border-slate-100 flex items-start justify-between gap-2">
            <div className="flex items-center gap-3 min-w-0">
              <div className="w-11 h-11 rounded-xl overflow-hidden bg-slate-100 shrink-0 flex items-center justify-center">
                {coverUrl ? (
                  <img src={coverUrl} alt="" className="w-full h-full object-cover" />
                ) : (
                  <Building2 className="w-5 h-5 text-slate-400" />
                )}
              </div>
              <div className="min-w-0">
                {businesses.length > 1 ? (
                  <select
                    value={selectedBusinessId ?? ""}
                    onChange={(e) => setSelectedBusinessId(Number(e.target.value))}
                    className="text-sm font-bold text-slate-900 bg-transparent -ml-1 pl-1 pr-1 py-0.5 rounded-md focus:outline-none focus:ring-2 focus:ring-brand/30 cursor-pointer max-w-full"
                  >
                    {businesses.map((b) => (
                      <option key={b.id} value={b.id}>{b.name}</option>
                    ))}
                  </select>
                ) : (
                  <p className="text-sm font-bold text-slate-900 truncate">{businesses[0].name}</p>
                )}
                {/* "Cevrimici" gibi gercekte olmayan bir canli durum
                    UYDURULMADI -- onayli rozeti gercek veriye dayanan tek
                    anlamli gosterge. */}
                {selectedBusiness?.verified && (
                  <p className="text-xs text-emerald-600 font-medium mt-0.5">✓ Onaylı İşletme</p>
                )}
              </div>
            </div>
            <button
              type="button"
              onClick={() => setSidebarOpen(false)}
              className="lg:hidden p-1 text-slate-400 hover:text-slate-600 cursor-pointer shrink-0"
              aria-label="Menüyü kapat"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {selectedBusiness?.suspended && (
            <div className="mx-4 mt-4 bg-red-50 border border-red-200 rounded-xl px-3 py-2.5">
              <p className="text-xs font-semibold text-red-700">Hesap silme sürecinde</p>
              <p className="text-xs text-red-600 mt-0.5">
                Sadece görüntüleme. <Link to="/profile" className="underline">İptal et</Link>
              </p>
            </div>
          )}

          <nav className="p-3">
            <p className="px-2.5 pt-2 pb-1.5 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
              Yönetim Sekmeleri
            </p>
            {TABS.map((tab) => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.key;
              const count = counts[tab.key];
              return (
                <button
                  key={tab.key}
                  onClick={() => {
                    setActiveTab(tab.key);
                    setSidebarOpen(false);
                  }}
                  className={`w-full flex items-center gap-2.5 px-2.5 py-2.5 mb-0.5 rounded-xl text-sm font-medium transition-colors cursor-pointer ${
                    isActive ? "bg-brand text-white" : "text-slate-600 hover:bg-slate-50"
                  }`}
                >
                  <Icon className={`w-4 h-4 shrink-0 ${isActive ? "text-white" : "text-slate-400"}`} />
                  <span className="flex-1 text-left truncate">{tab.label}</span>
                  {!!count && tab.badgeStyle && (
                    <span
                      className={`shrink-0 min-w-5 h-5 px-1.5 rounded-full text-[11px] font-bold flex items-center justify-center ${
                        tab.badgeStyle === "amber" ? "bg-amber-400 text-amber-950" : "bg-emerald-400 text-emerald-950"
                      }`}
                    >
                      {count}
                    </span>
                  )}
                  {!!count && !tab.badgeStyle && (
                    <span className={`shrink-0 text-xs font-semibold ${isActive ? "text-white/70" : "text-slate-400"}`}>
                      {count}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>
        </aside>

        {/* İçerik */}
        <div className="flex-1 min-w-0">
          {activeTab === "inbox" && (
            <InboxTab businessId={selectedBusinessId} suspended={!!selectedBusiness?.suspended} onCountChange={makeCountHandler("inbox")} />
          )}
          {activeTab === "approved" && (
            <ApprovedTab businessId={selectedBusinessId} suspended={!!selectedBusiness?.suspended} onCountChange={makeCountHandler("approved")} />
          )}
          {activeTab === "services" && (
            <ServicesTab businessId={selectedBusinessId} suspended={!!selectedBusiness?.suspended} onCountChange={makeCountHandler("services")} />
          )}
          {activeTab === "staff" && (
            <StaffTab businessId={selectedBusinessId} suspended={!!selectedBusiness?.suspended} onCountChange={makeCountHandler("staff")} />
          )}
          {activeTab === "hours" && <WorkingHoursTab businessId={selectedBusinessId} suspended={!!selectedBusiness?.suspended} />}
          {activeTab === "location" && <LocationInfoTab businessId={selectedBusinessId} suspended={!!selectedBusiness?.suspended} />}
          {activeTab === "gallery" && <GalleryTab businessId={selectedBusinessId} suspended={!!selectedBusiness?.suspended} />}
        </div>
      </div>
    </div>
  );
}
