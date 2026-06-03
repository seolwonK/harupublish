import type { Metadata } from "next";
import { AppHeader, LegalStyles } from "../components";

export const metadata: Metadata = {
  title: "Privacy Policy | Haru",
  description: "Haru Privacy Policy — how the Haru Platform collects, processes, retains, and protects your personal information."
};

type Clause = {
  number: number;
  title: string;
  body: React.ReactNode;
};

const clauses: Clause[] = [
  {
    number: 1,
    title: "Purpose of Processing",
    body: (
      <p>
        The Company processes personal information for purposes including website sign-up, provision of goods and
        services (Lesson matching), handling of user complaints, and service improvement.
      </p>
    )
  },
  {
    number: 2,
    title: "Personal Information Items Collected",
    body: (
      <ol className="legal-list">
        <li>
          <strong>Identifiers:</strong> Name, email address, country, time zone, profile picture, etc.
        </li>
        <li>
          <strong>Commercial Information:</strong> Transaction records of Lessons and Haru Credits, purchase history, and
          billing address (processed via Lemon Squeezy or Dodo Payments).
        </li>
        <li>
          <strong>Sensory Data:</strong> Microphone activity and camera access for real-time Lesson delivery.
        </li>
        <li>
          <strong>Lesson Recordings:</strong> Real-time video lessons are recorded and stored for a period of one (1)
          month. This data is processed specifically to verify that Lessons were delivered appropriately and to review
          any complaints or disputes that may arise between Students and Tutors.
        </li>
      </ol>
    )
  },
  {
    number: 3,
    title: "Period of Retention",
    body: (
      <ol className="legal-list">
        <li>
          <strong>Lesson Recordings:</strong> 1 month (for dispute resolution and quality monitoring).
        </li>
        <li>
          <strong>Payment Records:</strong> 5 years (as required by statutory law).
        </li>
      </ol>
    )
  },
  {
    number: 4,
    title: "Outsourcing & Third-Party Provision",
    body: (
      <p>
        The Company outsources data processing to: (1) Payment Processing: Lemon Squeezy and Dodo Payments (Merchants of
        Record), (2) Customer Support: Freshdesk, (3) Lesson Infrastructure: Video conferencing providers.
      </p>
    )
  },
  {
    number: 5,
    title: "Global Data Transfer",
    body: (
      <p>
        The Company takes reasonable measures to protect personal information during cross-border transfers. Data may be
        stored or processed on servers located in South Korea or the United States.
      </p>
    )
  },
  {
    number: 6,
    title: "User Rights",
    body: (
      <p>
        Users may, at any time, exercise their rights to access, correct, delete, or restrict the processing of their
        personal information by contacting us at{" "}
        <a href="mailto:bridgoworld@gmail.com">bridgoworld@gmail.com</a>.
      </p>
    )
  },
  {
    number: 7,
    title: "Contact Information",
    body: (
      <p>
        For questions regarding this Privacy Policy or personal information processing, please contact: Email:{" "}
        <a href="mailto:bridgoworld@gmail.com">bridgoworld@gmail.com</a>
      </p>
    )
  }
];

export default function PrivacyPage() {
  return (
    <main className="page-shell">
      <AppHeader />
      <article className="legal-doc">
        <header className="legal-head">
          <span className="legal-eyebrow">Legal</span>
          <h1>Privacy Policy of Haru Platform</h1>
          <p className="legal-meta">Effective Date: May 13, 2026</p>
        </header>

        <div className="legal-body">
          {clauses.map((clause) => (
            <section className="legal-article" key={clause.number} id={`article-${clause.number}`}>
              <h2 className="legal-article-title">
                <span className="legal-article-num">Article {clause.number}</span>
                <span>{clause.title}</span>
              </h2>
              <div className="legal-article-body">{clause.body}</div>
            </section>
          ))}

          <div className="legal-contact">
            <strong>Contact</strong>
            For any privacy-related inquiries, reach us at{" "}
            <a href="mailto:bridgoworld@gmail.com">bridgoworld@gmail.com</a>.
          </div>
        </div>
      </article>

      <LegalStyles />
    </main>
  );
}
