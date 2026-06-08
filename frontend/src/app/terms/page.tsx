import type { Metadata } from "next";
import { AppHeader, LegalStyles } from "../components";

export const metadata: Metadata = {
  title: "User Service Agreement | Haru",
  description: "Haru User Service Agreement — the rights, obligations, and responsibilities governing the use of the Haru Platform and its Services."
};

type Clause = {
  number: number;
  title: string;
  body: React.ReactNode;
};

const clauses: Clause[] = [
  {
    number: 1,
    title: "Purpose",
    body: (
      <p>
        The purpose of these Terms and Conditions is to define the rights, obligations, and responsibilities of Haru
        (hereinafter referred to as the &ldquo;Company&rdquo;) and its Users in connection with the use of the
        Internet-related services, including real-time Korean language, K-pop, K-beauty, and lifestyle education
        (hereinafter referred to as the &ldquo;Services&rdquo;) provided by the Company through its Site and App.
      </p>
    )
  },
  {
    number: 2,
    title: "Definitions",
    body: (
      <ol className="legal-list">
        <li>
          &ldquo;Haru Platform&rdquo; means the virtual place of business established by the Company using information
          and communications facilities to provide Content and Lessons to its Users.
        </li>
        <li>
          &ldquo;User&rdquo; means an individual, whether a Member or a Non-member, who accesses the Platform in
          accordance with these Terms.
        </li>
        <li>
          &ldquo;Tutor&rdquo; means a Member who offers and delivers real-time educational services (Teacher Services)
          through the Platform.
        </li>
        <li>
          &ldquo;Student&rdquo; means a Member who purchases Lessons and Teacher Services through the Platform.
        </li>
        <li>
          &ldquo;Haru Credits&rdquo; means a virtual refund-based currency provided by the Company. Credits are not the
          primary object of purchase; rather, they are issued to Students in cases of valid Lesson cancellations or
          refunds to be used as a payment method for future Lesson bookings. Credits are displayed and valued in US
          Dollars (USD).
        </li>
        <li>
          &ldquo;Lesson Contract&rdquo; means a legally binding agreement entered into directly between the Student and
          the Tutor for the delivery of Teacher Services.
        </li>
      </ol>
    )
  },
  {
    number: 3,
    title: "Eligibility to Use the Platform",
    body: (
      <p>
        Users must be at least fourteen (14) years of age to use the Platform. By using the Platform, Users represent
        and warrant that they meet the minimum age requirement under applicable law. If the Company becomes aware that a
        User does not meet the minimum age requirement, the Company may suspend or terminate the account.
      </p>
    )
  },
  {
    number: 4,
    title: "Relationship of Parties",
    body: (
      <p>
        The Haru Platform is an online venue where registered Members may interact directly with one another. You
        understand and agree that Haru is not a party to any agreements entered into between Students and Tutors. Haru
        has no control over the conduct of Students or Tutors and disclaims all liability in this regard to the maximum
        extent permitted by law.
      </p>
    )
  },
  {
    number: 5,
    title: "Lesson Purchases & Financial Terms",
    body: (
      <ol className="legal-list">
        <li>
          <strong>Purchasing Lessons:</strong> Students purchase Teacher Services (Lessons) directly using Payment
          Methods authorized by the Company (e.g., Credit Card, PayPal) via our authorized resellers and Merchants of
          Record (Lemon Squeezy or Dodo Payments). All Lesson prices are indicated in US Dollars (USD).
        </li>
        <li>
          <strong>Haru Credits:</strong> Students do not purchase Haru Credits as a standalone product. Haru Credits are
          primarily used as a mechanism for handling refunds. When a booked Lesson is canceled under valid conditions,
          the refund is credited to the Student&rsquo;s account in the form of Haru Credits, which can be applied to
          future Lesson purchases.
        </li>
        <li>
          <strong>No Cash Refunds:</strong> Authorized payments for Lessons are final. No cash refunds to the original
          payment method will be issued, except where required by applicable law. Refunds are provided solely in Haru
          Credits.
        </li>
        <li>
          <strong>Expiration:</strong> Haru Credits in a Haru Account will remain valid as long as the Member remains
          active. If a Member&rsquo;s account has been inactive for more than twelve (12) months, the Haru Credits will
          expire.
        </li>
        <li>
          <strong>Platform Fees &amp; Additional Charges:</strong> The Company may charge platform service fees in
          connection with the use of the Platform. Applicable fees, charges, or deductions, if any, may be disclosed
          within the Platform. Additional costs related to payments, withdrawals, currency conversion, exchange rate
          differences, bank charges, or third-party payment services may apply.
        </li>
      </ol>
    )
  },
  {
    number: 6,
    title: "Booking and Cancellation Policy",
    body: (
      <ol className="legal-list">
        <li>
          <strong>Reservation:</strong> A Lesson is confirmed only when the Student selects a Tutor and completes the
          payment for the Lesson.
        </li>
        <li>
          <strong>Cancellation Window:</strong> A Student may cancel or reschedule a Lesson through the Platform up to
          three (3) hours prior to the scheduled start time.
        </li>
        <li>
          <strong>Strict Late Cancellation &amp; No-Show Policy:</strong> If a Student attempts to cancel or reschedule
          within three (3) hours of the Lesson, it shall be deemed a &lsquo;Late Cancellation&rsquo;. In such cases, no
          refund of Haru Credits shall be issued, and 100% of the Lesson fee will be transferred to the Tutor.
        </li>
        <li>
          <strong>Refund Method:</strong> In cases of valid cancellation (more than 3 hours prior) or cancellation by
          the Tutor, the refund will be provided solely in the form of Haru Credits to the Student&rsquo;s Wallet. These
          Credits are fixed in USD value.
        </li>
      </ol>
    )
  },
  {
    number: 7,
    title: "Intellectual Property & Ownership",
    body: (
      <ol className="legal-list">
        <li>
          <strong>Company Ownership:</strong> The Company owns the Platform, its visual interfaces, graphics, design,
          and proprietary content.
        </li>
        <li>
          <strong>Recording Policy:</strong> The Company records real-time video lessons for quality assurance and
          dispute resolution (see Privacy Policy). Unauthorized recording, reproduction, or distribution by Users is
          strictly prohibited.
        </li>
      </ol>
    )
  },
  {
    number: 8,
    title: "User Conduct",
    body: (
      <p>
        Users agree not to engage in &lsquo;Friendly Fraud&rsquo; by initiating disputes with payment providers (Lemon
        Squeezy or Dodo Payments) to bypass the no-cash-refund policy.
      </p>
    )
  },
  {
    number: 9,
    title: "Account Suspension & Termination",
    body: (
      <p>
        The Company reserves the right to suspend or terminate accounts for violations of these Terms, fraud, abuse,
        misconduct, or any activity deemed harmful to the Platform or its Users.
      </p>
    )
  },
  {
    number: 10,
    title: "Limitation of Liability",
    body: (
      <p>
        To the maximum extent permitted by law, the Company shall not be liable for indirect, incidental, consequential,
        or special damages arising from the use of the Platform or Services.
      </p>
    )
  },
  {
    number: 11,
    title: "Governing Law & Dispute Resolution",
    body: (
      <p>
        These Terms shall be governed by and construed in accordance with the laws of the Republic of Korea. Any dispute
        arising out of or relating to these Terms or the Services shall be subject to the exclusive jurisdiction of the
        courts of the Republic of Korea.
      </p>
    )
  }
];

export default function TermsPage() {
  return (
    <main className="page-shell">
      <AppHeader />
      <article className="legal-doc">
        <header className="legal-head">
          <span className="legal-eyebrow">Legal</span>
          <h1>Haru User Service Agreement</h1>
          <p className="legal-meta">Last Updated: May 13, 2026</p>
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
        </div>
      </article>

      <LegalStyles />
    </main>
  );
}
