import { useState } from "react"
import { sentMailApi } from "../service/PasswordGeneratorApi"

const SendMail = ({ darkMode, generatedPassword }) => {

    const [recipientEmail, setRecipientEmail] = useState('')
    const [recipientName, setRecipientName] = useState('')
    const [isEmailEmpty, setIsEmailEmpty] = useState(false)
    const [isNameEmpty, setIsNameEmpty] = useState(false)
    const [sendText, setSendText] = useState('send')
    const [status, setStatus] = useState()

    function sentMail(e) {
        e.preventDefault()
        setSendText('validating...')

        if (validateInputs()) {
            setSendText('sending...')
            const reqData = { name: recipientName, email: recipientEmail, password: generatedPassword }
            sentMailApi(reqData)
                .then(response => {
                    if (response.status === 200) {
                        setSendText('done')
                        setStatus({ success: true, message: 'Email sent successfully!' });
                        setTimeout(function () {
                            setSendText("send")
                            setStatus(null);
                        }, 3000);
                    }
                })
                .catch(error => {
                    console.error(error);
                    setSendText('send');
                    setStatus({ success: false, message: 'Failed to send email. Please try again.' });
                    setTimeout(() => setStatus(null), 3000);
                })
        } else {
            setSendText('send');
        }
    }

    function validateInputs() {
        let valid = true

        if (recipientEmail.trim()) {
            setIsEmailEmpty(false)
        } else {
            setIsEmailEmpty(true)
            valid = false
        }

        if (recipientName.trim()) {
            setIsNameEmpty(false)
        } else {
            setIsNameEmpty(true)
            valid = false
        }

        return valid
    }

    return (
        <>
            <div className="mt-4">
                <p className="font-bold border-b w-28 border-indigo-600 pb-1 mb-4">Send to Email</p>
                <div className="">
                    <input
                        className={
                            (darkMode == false) ? "bg-indigo-50 border-0 p-2 rounded-md w-full font-medium text-gray-800 focus:ring-2 focus:ring-indigo-400 focus:outline-none transition-all duration-300" :
                                "bg-gray-700 border-0 p-2 rounded-md w-full text-white font-medium focus:ring-2 focus:ring-purple-500 focus:outline-none transition-all duration-300"
                        }
                        type="text"
                        placeholder='Name'
                        name="recipientName"
                        value={recipientName}
                        onChange={(e) => setRecipientName(e.target.value)}
                    />
                    {
                        isNameEmpty &&
                        <div className="text-red-600 float-end">Invalid</div>
                    }
                </div>
                <div className="">
                    <input
                        className={
                            (darkMode == false) ? "bg-indigo-50 border-0 p-2 rounded-md w-full mt-2 font-medium text-gray-800 focus:ring-2 focus:ring-indigo-400 focus:outline-none transition-all duration-300" :
                                "bg-gray-700 border-0 p-2 rounded-md w-full mt-2 text-white font-medium focus:ring-2 focus:ring-purple-500 focus:outline-none transition-all duration-300"
                        }
                        type="email"
                        placeholder="Email Address"
                        name="recipientEmail"
                        value={recipientEmail}
                        onChange={(e) => setRecipientEmail(e.target.value)}
                    />
                    {
                        isEmailEmpty &&
                        <label className="text-red-600 float-end">Invalid</label>
                    }
                </div>
                <div>
                    <button
                        className='mt-2 rounded-md shadow px-4 py-2 bg-gradient-to-r from-indigo-600 to-purple-600 text-white font-bold hover:from-indigo-700 hover:to-purple-700 capitalize transition-all duration-300'
                        onClick={(e) => sentMail(e)}>
                        {sendText}
                    </button>
                    {status && (
                        <div className={`mt-2 text-sm ${status.success ? 'text-green-600' : 'text-red-600'}`}>
                            {status.message}
                        </div>
                    )}
                </div>
            </div>
        </>
    )
}

export default SendMail;