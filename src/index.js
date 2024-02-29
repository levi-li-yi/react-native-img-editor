/*
 * @Author: Levi Li
 * @Date: 2023-12-04 11:53:23
 * @description:
 */
import { NativeModules } from 'react-native';
const { PhotoEditor } = NativeModules;

let exportObject = {};

const defaultOptions = {
  path: '',
  stickers: [],
  color: 'green',
};

exportObject = {
  open: (optionsEditor) => {
    const options = {
      ...defaultOptions,
      ...optionsEditor,
    };
    return new Promise(async (resolve, reject) => {
      try {
        const response = await PhotoEditor.open(options);
        if (response) {
          resolve(response);
          return true;
        }
        throw 'ERROR_UNKNOW';
      } catch (e) {
        reject(e);
      }
    });
  },
};

export default exportObject;
