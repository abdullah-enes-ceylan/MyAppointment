import { NavLink } from "react-router-dom";

const icon = {
  width: 22,
  height: 22,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round",
  strokeLinejoin: "round",
};

const TABS = [
  {
    to: "/",
    label: "Keşfet",
    Icon: () => (
      <svg {...icon}>
        <path d="M3 10.5L12 3l9 7.5" />
        <path d="M5.5 9.5V20h13V9.5" />
      </svg>
    ),
  },
  {
    to: "/appointments",
    label: "Randevularım",
    Icon: () => (
      <svg {...icon}>
        <rect x="3" y="5" width="18" height="16" rx="2.5" />
        <path d="M3 10h18M8 3v4M16 3v4" />
      </svg>
    ),
  },
  {
    to: "/favorites",
    label: "Favorilerim",
    Icon: () => (
      <svg {...icon}>
        <path d="M12 20.3l-1.4-1.3C5.4 14.4 2 11.3 2 7.6 2 4.9 4.1 3 6.6 3c1.6 0 3.1.8 4 2 .9-1.2 2.4-2 4-2C17.9 3 20 4.9 20 7.6c0 3.7-3.4 6.8-8.6 11.4z" />
      </svg>
    ),
  },
  {
    to: "/profile",
    label: "Profil",
    Icon: () => (
      <svg {...icon}>
        <circle cx="12" cy="8" r="3.6" />
        <path d="M4.5 20.5a7.5 7.5 0 0 1 15 0" />
      </svg>
    ),
  },
];

// Sadece mobilde ve sadece giriş yapmışken görünür. Giriş yapmamış
// kullanıcıda gizli çünkü 4 sekmenin 3'ü ProtectedRoute arkasında --
// hepsi login'e atsaydı çubuk işlevsiz bir tuzak olurdu.
export default function BottomTabBar() {
  return (
    <nav className="sm:hidden fixed bottom-0 inset-x-0 z-40 bg-white border-t border-slate-200 pb-[env(safe-area-inset-bottom)]">
      <div className="grid grid-cols-4">
        {TABS.map(({ to, label, Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === "/"}
            className={({ isActive }) =>
              `flex flex-col items-center justify-center gap-1 py-2.5 text-[10px] font-medium transition-colors ${
                isActive ? "text-[#161b33]" : "text-slate-400"
              }`
            }
          >
            <Icon />
            <span>{label}</span>
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
