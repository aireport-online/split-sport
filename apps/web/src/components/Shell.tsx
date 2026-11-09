import { LayoutGrid, Menu, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router";
import { useGroups } from "../lib/queries";
import { useUser } from "../lib/session";
import { Avatar, Button, Tile } from "./ui";

export function BrandMark() {
  return (
    <svg className="brand__mark" viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="var(--accent)" />
      <path d="M9 8h14v13l-3.5-2.2L16 21l-3.5-2.2L9 21z" fill="#fff" />
      <path d="M16 8v13" stroke="var(--accent)" strokeWidth="2" strokeDasharray="2.4 2" />
    </svg>
  );
}

const navClass = ({ isActive }: { isActive: boolean }) => `nav-item${isActive ? " active" : ""}`;

/** The frame around every signed-in screen: sidebar on desktop, a top bar with a menu sheet on a phone. */
export function Shell() {
  const user = useUser();
  const groups = useGroups();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => setMenuOpen(false), [location.pathname]);

  return (
    <div className="shell">
      <header className="topbar">
        <Button variant="ghost" icon aria-label="Menu" aria-expanded={menuOpen} onClick={() => setMenuOpen(true)}>
          <Menu size={20} />
        </Button>
        <Link to="/" className="brand">
          <BrandMark />
          Split
        </Link>
      </header>

      <aside className="sidebar" data-open={menuOpen}>
        <div className="sidebar__close">
          <Button variant="ghost" icon aria-label="Menu sluiten" onClick={() => setMenuOpen(false)}>
            <X size={20} />
          </Button>
        </div>
        <Link to="/" className="brand">
          <BrandMark />
          Split
        </Link>
        <nav className="nav" aria-label="Hoofdmenu">
          <NavLink to="/" end className={navClass}>
            <LayoutGrid />
            <span className="nav-item__text">Groepen</span>
          </NavLink>
          {groups.data && groups.data.length > 0 && <p className="nav__label">Je groepen</p>}
          {groups.data?.map((group) => (
            <NavLink key={group.id} to={`/groups/${group.id}`} className={navClass}>
              <Tile name={group.name} color={group.color} size="small" />
              <span className="nav-item__text">{group.name}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar__bottom">
          <NavLink to="/profile" className={navClass}>
            <Avatar name={user.name} color="slate" small />
            <span className="nav-item__text">{user.name}</span>
          </NavLink>
        </div>
      </aside>

      <main className="canvas">
        <Outlet />
      </main>
    </div>
  );
}
