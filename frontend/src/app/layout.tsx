import type { Metadata } from "next";
import localFont from "next/font/local";
import { AuthProvider } from "./auth";
import { Footer } from "./components";
import { CurrencyProvider } from "./currency";
import "./globals.css";

const gmarketSans = localFont({
  src: [
    {
      path: "../../fonts/GmarketSansTTFLight.ttf",
      weight: "300",
      style: "normal"
    },
    {
      path: "../../fonts/GmarketSansTTFMedium.ttf",
      weight: "500",
      style: "normal"
    },
    {
      path: "../../fonts/GmarketSansTTFBold.ttf",
      weight: "700",
      style: "normal"
    }
  ],
  variable: "--font-gmarket-sans",
  display: "swap"
});

export const metadata: Metadata = {
  title: "Haru",
  description: "Haru frontend",
  icons: {
    icon: "/images/haru-logo-cropped.png",
    shortcut: "/images/haru-logo-cropped.png",
    apple: "/images/haru-logo-cropped.png"
  }
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body className={gmarketSans.variable}>
        <AuthProvider>
          <CurrencyProvider>
            {children}
            <Footer />
          </CurrencyProvider>
        </AuthProvider>
      </body>
    </html>
  );
}
