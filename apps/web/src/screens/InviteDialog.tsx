import type { GroupDetail } from "@split/core";
import { Check, Copy, Share2 } from "lucide-react";
import { useState } from "react";
import { Button, Dialog } from "../components/ui";

export const inviteLink = (code: string) => `${window.location.origin}/join/${code}`;

/** Shows the invite code and link of a group, with copy and (where supported) the system share sheet. */
export function InviteDialog({ group, onClose }: { group: GroupDetail; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  const link = inviteLink(group.inviteCode);
  const text = `Doe mee met "${group.name}" op Split Sport`;

  const copy = async () => {
    await navigator.clipboard.writeText(link);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <Dialog title="Uitnodigen" narrow onClose={onClose}>
      <p className="muted">
        Deel de link of de code. Wie meedoet kiest zijn eigen naam uit het team, of komt er als nieuw teamlid bij.
      </p>
      <p className="invite-code" style={{ margin: "20px 0 4px" }}>
        {group.inviteCode}
      </p>
      <p className="muted small" style={{ overflowWrap: "anywhere" }}>
        {link}
      </p>
      <div className="panel__actions">
        <Button variant="primary" onClick={copy}>
          {copied ? <Check size={16} /> : <Copy size={16} />}
          {copied ? "Gekopieerd" : "Link kopiëren"}
        </Button>
        {typeof navigator.share === "function" && (
          <Button onClick={() => void navigator.share({ title: "Split Sport", text, url: link }).catch(() => {})}>
            <Share2 size={16} /> Deel uitnodiging
          </Button>
        )}
      </div>
    </Dialog>
  );
}
