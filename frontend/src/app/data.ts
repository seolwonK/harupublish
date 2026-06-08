export type Tutor = {
  id: string;
  name: string;
  subject: string;
  languages: string;
  rating: number;
  reviews: number;
  price: string;
  location: string;
  avatar: string;
  accent: string;
};

export type Booking = {
  tutor: string;
  date: string;
  time: string;
  duration: string;
  status: "예약" | "완료" | "취소";
  avatar: string;
};

export const tutors: Tutor[] = [
  {
    id: "kim-ji-hyun",
    name: "김지현 튜터",
    subject: "한국어 회화",
    languages: "한국어 · 영어",
    rating: 4.9,
    reviews: 128,
    price: "25분 USD 13",
    location: "서울, 한국",
    avatar: "JH",
    accent: "from-violet-50 to-purple-100"
  },
  {
    id: "park-joon-ho",
    name: "박준호 튜터",
    subject: "TOPIK · 문법",
    languages: "한국어 · 영어",
    rating: 4.8,
    reviews: 86,
    price: "50분 USD 25",
    location: "부산, 한국",
    avatar: "JH",
    accent: "from-indigo-50 to-violet-100"
  },
  {
    id: "choi-in-seo",
    name: "최인서 튜터",
    subject: "비즈니스 한국어",
    languages: "한국어 · 일본어",
    rating: 4.9,
    reviews: 75,
    price: "25분 USD 13",
    location: "인천, 한국",
    avatar: "IS",
    accent: "from-purple-50 to-fuchsia-100"
  },
  {
    id: "jung-woo-jin",
    name: "정우진 튜터",
    subject: "입문 한국어",
    languages: "한국어 · 중국어",
    rating: 4.8,
    reviews: 63,
    price: "25분 USD 13",
    location: "대전, 한국",
    avatar: "WJ",
    accent: "from-violet-100 to-indigo-50"
  }
];

export const bookings: Booking[] = [
  {
    tutor: "김지현 튜터",
    date: "5월 22일(금)",
    time: "10:00",
    duration: "50분",
    status: "예약",
    avatar: "JH"
  },
  {
    tutor: "박준호 튜터",
    date: "5월 24일(일)",
    time: "20:00",
    duration: "25분",
    status: "예약",
    avatar: "JH"
  },
  {
    tutor: "이서연 튜터",
    date: "5월 16일(토)",
    time: "10:00",
    duration: "50분",
    status: "완료",
    avatar: "SY"
  },
  {
    tutor: "강태우 튜터",
    date: "5월 12일(화)",
    time: "11:00",
    duration: "25분",
    status: "취소",
    avatar: "TW"
  }
];

export const reviews = [
  { name: "Lucas", date: "2026.05.10", body: "말문이 막힐 때 자연스럽게 이어갈 표현을 알려줘서 회화가 훨씬 편해졌어요.", avatar: "L" },
  { name: "Sophie", date: "2026.04.29", body: "수업 흐름이 체계적이고 자료가 깔끔해요. 다음 수업에서 바로 복습할 수 있었어요.", avatar: "S" },
  { name: "Tom", date: "2026.04.15", body: "발음 교정이 구체적이라 녹음해서 비교하기 좋았습니다.", avatar: "T" }
];

export const conversations = [
  { name: "김지현 튜터", message: "오늘 교재 9쪽부터 볼게요.", time: "오전 9:20", unread: 2, avatar: "JH", online: true },
  { name: "박준호 튜터", message: "감사합니다 :)", time: "어제", unread: 0, avatar: "JH", online: false },
  { name: "최인서 튜터", message: "수업 자료 보내드릴게요.", time: "어제", unread: 0, avatar: "IS", online: false },
  { name: "Haru 알림", message: "수업이 30분 후 시작됩니다.", time: "오전 9:30", unread: 1, avatar: "H", online: false },
  { name: "운영팀", message: "문의하신 내용을 확인했습니다.", time: "5.19", unread: 0, avatar: "OP", online: false }
];

export const adminRows = [
  { name: "김민지", email: "minji.kim@email.com", career: "3년", submitted: "2026.05.20", status: "서류 검토" },
  { name: "이상훈", email: "sanghoon.lee@email.com", career: "5년", submitted: "2026.05.19", status: "샘플 수업" },
  { name: "박소연", email: "soyeon.park@email.com", career: "2년", submitted: "2026.05.18", status: "서류 검토" }
];
