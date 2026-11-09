import { Menu, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router";
import { useGroups } from "../lib/queries";
import { useUser } from "../lib/session";
import { Button, Mark, Wordmark } from "./ui";

const navClass = ({ isActive }: { isActive: boolean }) => `nav-item${isActive ? " active" : ""}`;
const groupClass = (state: { isActive: boolean }) => `${navClass(state)} nav-item--group`;

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
          <Menu size={22} />
        </Button>
        <Link to="/" aria-label="Split, naar je groepen">
          <Wordmark size="small" />
        </Link>
      </header>

      <aside className="sidebar" data-open={menuOpen}>
        <div className="sidebar__close">
          <Button variant="ghost" icon aria-label="Menu sluiten" onClick={() => setMenuOpen(false)}>
            <X size={22} />
          </Button>
        </div>
        <Link to="/" aria-label="Split, naar je groepen">
          <Wordmark />
        </Link>
        <nav className="nav" aria-label="Hoofdmenu">
          <NavLink to="/" end className={navClass}>
            <span className="nav-item__text">Groepen</span>
          </NavLink>
          {groups.data && groups.data.length > 0 && <p className="nav__label">Je groepen</p>}
          {groups.data?.map((group) => (
            <NavLink key={group.id} to={`/groups/${group.id}`} className={groupClass}>
              <span className="nav-item__text">{group.name}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar__bottom">
          <NavLink to="/profile" className="nav-item" aria-label={`Profiel van ${user.name}`}>
            <Mark color="amber">{user.name}</Mark>
          </NavLink>
        </div>
      </aside>

      <main className="canvas">
        <Outlet />
      </main>
    </div>
  );
}
