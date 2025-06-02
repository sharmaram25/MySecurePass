import '../css/generate.css'
import error from '../assets/error.svg'
import { generatePasswordApi, passwordStrengthVerifier, suggestPasswordApi } from "../service/PasswordGeneratorApi"
import Characters from './Characters';
import CharactersLength from './CharactersLength';
import SendMail from './SendMail';
import CopyToClipboard from "react-copy-to-clipboard";
import { useState, useEffect } from "react"

const Generate = ({ darkMode }) => {

    const [capitalAlphabet, setCapitalAlphabet] = useState(false)
    const [smallAlphabet, setSmallAlphabet] = useState(false)
    const [number, setNumber] = useState(false)
    const [specialCharacter, setSpecialCharacter] = useState(false)
    const [passwordLength, setPasswordLength] = useState(8)
    const [generatedPassword, setGeneratedPassword] = useState('MyPassword')
    const [suggestedPassword, setSuggestedPassword] = useState("");
    const [passwordToCopy, setPasswordToCopy] = useState('');
    const [copy, setCopy] = useState('COPY')
    const [suggestedPasswordCopy, setSuggestedPasswordCopy] = useState('suggest')
    const [strength, setStrength] = useState('poor')
    const [serverDown, setServerDown] = useState(false)


    useEffect(() => {
        callGeneratePasswordApi({ capitalAlphabet, smallAlphabet, number, specialCharacter, passwordLength })
    }, [capitalAlphabet, smallAlphabet, number, specialCharacter, passwordLength])

    useEffect(() => {
        setPasswordToCopy(generatedPassword)
    }, [generatedPassword])

    useEffect(() => {
        strengthVerifier(generatedPassword)
    }, [generatedPassword])

    useEffect(() => {
        callSuggestPasswordApi()
    }, [])

    const onCopyPassword = () => {
        setCopy('COPIED')
        setTimeout(function () {
            setCopy("Copy")
        }, 1000);
    }

    function refreshPassword() {
        callGeneratePasswordApi({ capitalAlphabet, smallAlphabet, number, specialCharacter, passwordLength })
    }

    function callGeneratePasswordApi(characters) {
        if (characters.capitalAlphabet == true || characters.smallAlphabet == true || characters.number == true || characters.specialCharacter == true) {
            generatePasswordApi(characters)
                .then(response => setGeneratedPassword(response.data))
                .catch(error => console.error(error))
        }
    }

    function callSuggestPasswordApi() {
        suggestPasswordApi()
            .then(response => setSuggestedPassword(response.data))
            .catch(() => setServerDown(true))
        console.log('server down: ' + serverDown);
    }

    function strengthVerifier(password) {
        if (password.length != 0) {
            passwordStrengthVerifier({ checkPassword: password })
                .then(response => setStrength(response.data))
                .catch(() => setServerDown(true))
        }
    }

    const onSuggestedPasswordCopy = () => {
        setSuggestedPasswordCopy('copied')
        setTimeout(function () {
            setSuggestedPasswordCopy('suggest')
        }, 1000);
    }

    return (
        <div className="flex justify-center items-center min-h-screen">
            {
                (serverDown != true) ?
                    <div className="container">
                        <div className="md:flex justify-center items-center p-5 md:p-0">
                            <div className="lg:w-5/12 mb-10 md:mb-0">
                                <div
                                    className={(darkMode == false) ? "md:text-2xl text-transparent bg-clip-text bg-gradient-to-r from-indigo-600 to-purple-700 uppercase w-full font-bold flex items-center" : "md:text-2xl text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 to-purple-500 uppercase w-full font-bold flex items-center"}
                                >
                                    <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6 mr-2 inline" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                        <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                                    </svg>
                                    {strength}
                                </div>
                                <div className="mb-3 font-bold text-3xl md:text-5xl w-9/12 truncate">
                                    {generatedPassword}
                                </div>
                                <div className="flex gap-1 md:gap-2 mt-5 font-bold">
                                    <CopyToClipboard
                                        text={passwordToCopy}
                                        onCopy={onCopyPassword}
                                    >
                                        <button className="rounded-full text-sm shadow px-4 py-1 bg-gradient-to-r from-indigo-600 to-purple-600 text-white hover:from-indigo-700 hover:to-purple-700 uppercase transition-all duration-300 security-pulse flex items-center"
                                        >
                                            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                                <path strokeLinecap="round" strokeLinejoin="round" d="M8 5H6a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2v-1M8 5a2 2 0 002 2h2a2 2 0 002-2M8 5a2 2 0 012-2h2a2 2 0 012 2m0 0h2a2 2 0 012 2v3m2 4H10m0 0l3-3m-3 3l3 3" />
                                            </svg>
                                            {copy}
                                        </button>
                                    </CopyToClipboard>
                                    <button
                                        className="rounded-full shadow text-sm px-4 py-1 bg-gradient-to-r from-indigo-600 to-purple-600 text-white hover:from-indigo-700 hover:to-purple-700 transition-all duration-300 flex items-center"
                                        onClick={() => refreshPassword()}
                                    >
                                        <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                            <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                                        </svg>
                                        REFRESH
                                    </button>
                                    <CopyToClipboard
                                        text={suggestedPassword}
                                        onCopy={onSuggestedPasswordCopy}
                                    >
                                        <button
                                            className="rounded-full text-sm shadow px-4 py-1 bg-gradient-to-r from-indigo-600 to-purple-600 text-white hover:from-indigo-700 hover:to-purple-700 uppercase transition-all duration-300 flex items-center"
                                            onClick={() => callSuggestPasswordApi()}
                                        >
                                            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                                <path strokeLinecap="round" strokeLinejoin="round" d="M12 11c0 3.517-1.009 6.799-2.753 9.571m-3.44-2.04l.054-.09A13.916 13.916 0 008 11a4 4 0 118 0c0 1.017-.07 2.019-.203 3m-2.118 6.844A21.88 21.88 0 0015.171 17m3.839 1.132c.645-2.266.99-4.659.99-7.132A8 8 0 008 4.07M3 15.364c.64-1.319 1-2.8 1-4.364 0-1.457.39-2.823 1.07-4" />
                                            </svg>
                                            {suggestedPasswordCopy}
                                        </button>
                                    </CopyToClipboard>
                                </div>
                            </div>
                            <div className="lg:w-4/12">
                                <CharactersLength passwordLength={passwordLength} setPasswordLength={setPasswordLength} />
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-3 md:gap-5">
                                    <Characters title="uppercase letters" value={capitalAlphabet} setCharacter={setCapitalAlphabet} />
                                    <Characters title="lowercase letters" value={smallAlphabet} setCharacter={setSmallAlphabet} />
                                    <Characters title="numbers" value={number} setCharacter={setNumber} />
                                    <Characters title="symbols" value={specialCharacter} setCharacter={setSpecialCharacter} />
                                </div>
                                <SendMail darkMode={darkMode} generatedPassword={generatedPassword} />
                            </div>
                        </div>
                    </div>
                    :
                    <div className="flex flex-col items-center justify-center">
                        <h1 className="text-4xl font-bold mb-4 text-center">Server Down</h1>
                        <p className="text-lg text-gray-600 mb-8 text-center">We apologize for the inconvenience, but the server is currently down. Please try again later.</p>
                        <img src={error} alt="Server Down Illustration" className="w-32 h-auto max-w-sm mb-8" />
                    </div>
            }
        </div>
    )
}

export default Generate