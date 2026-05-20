import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}"
  ],
  theme: {
    extend: {
      colors: {
        ink: "#1f2420",
        mist: "#ece4d5",
        pine: "#1f4e5f",
        coral: "#b7472a",
        gold: "#9a6b2f",
        paper: "#fffdf8",
        leaf: "#5d6f4f",
        lavender: {
          100: "#ebe4ff"
        },
        mint: {
          100: "#dff5eb"
        }
      },
      boxShadow: {
        soft: "0 14px 36px rgba(55, 47, 36, 0.12)"
      }
    }
  },
  plugins: []
};

export default config;
