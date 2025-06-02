import axios from "axios";

const localApiUrl = "http://localhost:5000/";
const productionApiUrl = "https://mysecurepass-api.onrender.com/";

const apiUrl = import.meta.env.PROD ? productionApiUrl : localApiUrl;

export const generatePasswordApi = (characters) => axios.post(apiUrl, characters)

export const passwordStrengthVerifier = (password) => axios.post(apiUrl + 'verifier', password)

export const sentMailApi = (requestData) => axios.post(apiUrl + 'mail', requestData)

export const suggestPasswordApi = () => axios.post(apiUrl+'suggest')