import { Component, Suspense } from 'react';
import { Redirect, Route, Router, Switch } from 'react-router-dom';
import { ToastContainer } from 'react-toastify';
import BigShield from '../ErrorBoundary/BigShield/BigShield';
import ErrorContainer from '../Error/ErrorContainer';
import '../../webflow.css';
import './App.css';
import '../../assets/css/APIMReactToastify.css';
import PageNotFound from '../PageNotFound/PageNotFound';
import HeaderContainer from '../Header/HeaderContainer';
import Spinner from '../Spinner/Spinner';
import Footer from '../Footer/Footer';
import { AsyncDashboardContainer, AsyncDetailPageContainer, AsyncLoginContainer } from './AsyncModules';

class App extends Component {
    render() {
        const { history } = this.props;
        const isLoading = true;
        return (
            <div className="App">
                <BigShield history={history}>
                    <ToastContainer />
                    <ErrorContainer />
                    <Suspense fallback={<Spinner isLoading={isLoading} />}>
                        <Router history={history}>
                            <>
                                <div className="content">
                                    <Route path="/(dashboard|tile/.*)/" component={HeaderContainer} />
                                    <Switch>
                                        <Route path="/" exact render={() => <Redirect replace to="/dashboard" />} />
                                        <Route
                                            path="/login"
                                            exact
                                            render={(props, state) => <AsyncLoginContainer {...props} {...state} />}
                                        />
                                        <Route
                                            exact
                                            path="/dashboard"
                                            render={(props, state) => (
                                                <BigShield>
                                                    <AsyncDashboardContainer {...props} {...state} />
                                                </BigShield>
                                            )}
                                        />
                                        <Route
                                            path="/tile/:tileID"
                                            render={(props, state) => (
                                                <BigShield history={history}>
                                                    <AsyncDetailPageContainer {...props} {...state} />
                                                </BigShield>
                                            )}
                                        />
                                        <Route
                                            render={(props, state) => (
                                                <BigShield history={history}>
                                                    <PageNotFound {...props} {...state} />
                                                </BigShield>
                                            )}
                                        />
                                    </Switch>
                                </div>
                                <Route path="/(dashboard|tile/.*)/" component={Footer} />
                            </>
                        </Router>
                    </Suspense>
                </BigShield>
            </div>
        );
    }
}

export default App;
