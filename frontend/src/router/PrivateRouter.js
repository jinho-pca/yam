import React from "react";
import { useSelector } from "react-redux";
import { useHistory } from "react-router";
import { Route, Redirect } from "react-router-dom";

import Swal from "sweetalert2";

const PrivateRouter = ({ component: Component, ...rest }) => {
  // const currentUser = useSelector((state) => state.user.login);
  const history = useHistory();
  const currentUser = { login: true };
  
  return (
    <Route
      {...rest}
      render={(props) => {
        !window.localStorage.getItem('token') && 
        Swal.fire({
          title: 'Error!',
          text: '다시 로그인해 주세요',
          icon: 'error',
          confirmButtonText: 'OK!',
          confirmButtonColor: '#497c5f'
        }).then((result) => {
          history.push('/signin');
        });
        return window.localStorage.getItem('token') ? <Component {...props} /> : <Redirect to="/signin" />;
        // !currentUser && alert("로그인이 필요한 페이지입니다.");
        // return currentUser ? <Component {...props} /> : <Redirect to="/signin" />;
      }}
    />
  );
};

export default PrivateRouter;
