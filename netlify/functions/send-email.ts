import { Handler } from '@netlify/functions';

// Note: In a real production environment, you would use 'nodemailer' or an API like SendGrid/Mailgun.
// For this demo, we mock the success to simulate the functionality as we cannot leak API keys here.

const handler: Handler = async (event) => {
  if (event.httpMethod !== 'POST') {
    return { statusCode: 405, body: 'Method Not Allowed' };
  }

  try {
    const { email, password } = JSON.parse(event.body || '{}');

    if (!email || !password) {
      return { statusCode: 400, body: 'Missing email or password' };
    }

    // --- MOCK SENDING LOGIC ---
    // const transporter = nodemailer.createTransport({ ...process.env.SMTP_CONFIG... });
    // await transporter.sendMail({
    //   from: process.env.SENDER_EMAIL,
    //   to: email,
    //   subject: 'Your Generated Password',
    //   text: `Your password is: ${password}\n\nFor security, please change this if you suspect interception.`
    // });
    
    // Simulate network delay
    await new Promise(resolve => setTimeout(resolve, 800));

    console.log(`[SECURE LOG] Password email requested for ${email.replace(/(.{2})(.*)(@.*)/, '$1***$3')}`);

    return {
      statusCode: 200,
      body: JSON.stringify({ message: 'Email sent successfully' }),
    };
  } catch (error) {
    console.error('Email error:', error);
    return { statusCode: 500, body: 'Internal Server Error' };
  }
};

export { handler };
