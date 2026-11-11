import { redirect } from 'next/navigation';

// This app is the back office only; customer-facing screens live in a separate B2C app.
export default function Home() {
  redirect('/admin');
}
