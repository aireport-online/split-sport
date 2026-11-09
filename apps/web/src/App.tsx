import { Navigate, Outlet, Route, Routes, useLocation } from "react-router";
import { Shell } from "./components/Shell";
import { useSession } from "./lib/session";
import { AuthScreen } from "./screens/AuthScreen";
import { GroupScreen } from "./screens/GroupScreen";
import { GroupSettingsScreen } from "./screens/GroupSettingsScreen";
import { GroupsScreen } from "./screens/GroupsScreen";
import { JoinScreen } from "./screens/JoinScreen";
import { ProfileScreen } from "./screens/ProfileScreen";

/** Sends signed-out visitors to the login and remembers where they were going (e.g. an invite link). */
function RequireUser() {
  const { user, loading } = useSession();
  const location = useLocation();
  if (loading) return null;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<AuthScreen mode="login" />} />
      <Route path="/register" element={<AuthScreen mode="register" />} />
      <Route element={<RequireUser />}>
        <Route element={<Shell />}>
          <Route index element={<GroupsScreen />} />
          <Route path="/groups/:groupId" element={<GroupScreen />} />
          <Route path="/groups/:groupId/settings" element={<GroupSettingsScreen />} />
          <Route path="/join" element={<JoinScreen />} />
          <Route path="/join/:code" element={<JoinScreen />} />
          <Route path="/profile" element={<ProfileScreen />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Route>
    </Routes>
  );
}
