import './app/styles';
import {createRoot} from 'react-dom/client';
import {App} from './app/App';

const appRoot=import.meta.hot?.data.root??createRoot(document.getElementById('root')!);
if(import.meta.hot)import.meta.hot.data.root=appRoot;
appRoot.render(<App/>);
