import { useEffect, useState } from "preact/hooks";
import { type Faq as FaqData, isOpenableFaqUrl, resolveFaq } from "../shared/backend.ts";
import { SITE_URL } from "../shared/config.ts";
import { Button, Card, TopBar } from "./components.tsx";
import { useEnv } from "./env.tsx";
import { call } from "./rpc.ts";

/** The questions, from the backend only: the extension ships none. Loading, unavailable and empty are states. */
export function Faq() {
  const { t } = useEnv();
  const [faq, setFaq] = useState<FaqData | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async (force: boolean) => {
    setLoading(true);
    try {
      const answer = await call("getFaq", force);
      setFaq(answer.faq);
    } catch {
      // Unavailable is a state of its own below.
    }
    setLoading(false);
  };
  useEffect(() => {
    void load(false);
  }, []);

  return (
    <div class="frame">
      <TopBar title={t.t("faq_title")} />
      <main class="scroll">
        {faq === null ? (
          loading ? (
            <p class="body">{t.t("faq_loading")}</p>
          ) : (
            <Card padded>
              <p class="body" style="margin:0 0 12px">
                {t.t("faq_unavailable")}
              </p>
              <Button variant="secondary" onClick={() => void load(true)}>
                {t.t("faq_retry")}
              </Button>
            </Card>
          )
        ) : faq.items.length === 0 ? (
          <p class="body">{t.t("faq_empty")}</p>
        ) : (
          <Card>
            {faq.items.map((item) => {
              const href = item.link && isOpenableFaqUrl(item.link.url) ? (item.link.url.startsWith("/") ? `${SITE_URL}${item.link.url}` : item.link.url) : null;
              return (
                <details class="faq-item" key={item.key}>
                  <summary>{resolveFaq(item.question, t.language)}</summary>
                  <div class="answer">
                    {resolveFaq(item.answer, t.language)}
                    {href && item.link && (
                      <div style="margin-top:10px">
                        <a href={href} target="_blank" rel="noopener noreferrer">
                          {resolveFaq(item.link.label, t.language)}
                        </a>
                      </div>
                    )}
                  </div>
                </details>
              );
            })}
          </Card>
        )}
      </main>
    </div>
  );
}
